package dev.zlddba.moshiapp.activities.mainPage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.data.db.QaLogDao
import dev.zlddba.moshiapp.data.db.QaLogEntity
import dev.zlddba.moshiapp.data.prefs.CloudConfigPrefs
import dev.zlddba.moshiapp.data.prefs.ModelPrefs
import dev.zlddba.moshiapp.data.prefs.SecurityPrefs
import dev.zlddba.moshiapp.data.repo.IngestRepository
import dev.zlddba.moshiapp.domain.qa.QaOrchestrator
import dev.zlddba.moshiapp.domain.retrieve.RetrieveService
import dev.zlddba.moshiapp.engine.local.BackendKind
import dev.zlddba.moshiapp.engine.local.LiteRtLlmEngine
import dev.zlddba.moshiapp.ingest.vision.StreamAsrModelManager
import dev.zlddba.moshiapp.ingest.vision.StreamingSpeechRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ChatViewModel(context: Context) : ViewModel() {

    data class ChatUiState(
        val isCloudEngine: Boolean = false,
        val backend: String = BackendKind.CPU.name,
        val messages: List<ChatMessage> = emptyList(),
        val streamingText: String = "",
        val streamingThinking: String = "",
        val isGenerating: Boolean = false,
        val modelMissing: Boolean = false,
        val draft: String = "",
        val voicePhase: VoiceInputPhase = VoiceInputPhase.IDLE
    ) {
        enum class Role { USER, ASSISTANT, REFUSAL }

        enum class VoiceInputPhase { IDLE, RECORDING }

        data class ChatMessage(
            val role: Role,
            val text: String = "",
            val thinking: String = "",
            val sources: List<ChatSource> = emptyList(),
            val autoExpandThinking: Boolean = false
        )

        data class ChatSource(
            val chunkId: Int,
            val noteId: String,
            val title: String,
            val pageNo: Int?
        )
    }

    sealed interface ChatEvent {
        data object Init : ChatEvent
        data object RefreshCloud : ChatEvent
        data class Send(val text: String) : ChatEvent
        data object Stop : ChatEvent
        data object Clear : ChatEvent
        data object DownloadModel : ChatEvent
        data class BackendSelected(val kind: BackendKind) : ChatEvent
        data class SourceClick(val source: ChatUiState.ChatSource) : ChatEvent
        data object VoiceInput : ChatEvent
        data class PermissionResult(val granted: Boolean) : ChatEvent
        data class DraftChanged(val text: String) : ChatEvent
    }

    sealed interface ChatEffect {
        data class OpenDetail(
            val noteId: String,
            val chunkId: Int,
            val keyword: String
        ) : ChatEffect

        data object OpenModelPage : ChatEffect
        data class ShowToast(val messageRes: Int) : ChatEffect
        data object RequestAudioPermission : ChatEffect
    }

    private val appContext = context.applicationContext
    private val modelPrefs = ModelPrefs(appContext)

    private val _uiState = MutableStateFlow(
        ChatUiState(backend = modelPrefs.currentBackend())
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ChatEffect>(Channel.BUFFERED)
    val effects: Flow<ChatEffect> = _effects.receiveAsFlow()

    private val streamBuffer = StringBuilder()
    private val commitLock = Any()
    private var generationJob: Job? = null
    private var warmedUp = false
    private var streamingAsr: StreamingSpeechRecognizer? = null
    private var voiceBase = ""
    private var voiceTimerJob: Job? = null

    @Volatile
    private var voiceText = ""

    private sealed interface PersistOp {
        data object Load : PersistOp
        data class Save(val message: ChatUiState.ChatMessage) : PersistOp
        data object ClearAll : PersistOp
    }

    private val persistOps = Channel<PersistOp>(Channel.UNLIMITED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = MoshiDatabase.get(appContext).qaLogDao()
            for (op in persistOps) {
                try {
                    when (op) {
                        PersistOp.Load -> restoreHistory(dao)
                        is PersistOp.Save -> {
                            if (SecurityPrefs(appContext).isQaHistoryEnabled()) {
                                dao.insert(op.message.toLog())
                                dao.trim(HISTORY_KEEP)
                            }
                        }

                        PersistOp.ClearAll -> dao.deleteAll()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "persist op failed: ${op::class.simpleName}", e)
                }
            }
        }
        persistOps.trySend(PersistOp.Load)
        viewModelScope.launch {
            IngestRepository.libraryCleared.collect {
                clear()
            }
        }
    }

    fun onEvent(event: ChatEvent) {
        when (event) {
            ChatEvent.Init -> warmUp()
            ChatEvent.RefreshCloud -> refreshCloud()
            is ChatEvent.Send -> send(event.text)
            ChatEvent.Stop -> stop()
            ChatEvent.Clear -> clear()
            ChatEvent.DownloadModel -> sendEffect(ChatEffect.OpenModelPage)
            is ChatEvent.BackendSelected -> selectBackend(event.kind)
            is ChatEvent.SourceClick -> sendEffect(
                ChatEffect.OpenDetail(
                    noteId = event.source.noteId,
                    chunkId = event.source.chunkId,
                    keyword = lastQuestion()
                )
            )

            ChatEvent.VoiceInput -> toggleVoiceInput()
            is ChatEvent.PermissionResult -> {
                if (event.granted) {
                    startVoiceRecording()
                } else {
                    sendEffect(ChatEffect.ShowToast(R.string.voice_permission_denied))
                }
            }

            is ChatEvent.DraftChanged -> _uiState.update { it.copy(draft = event.text) }
        }
    }

    private fun warmUp() {
        if (warmedUp) return
        warmedUp = true
        refreshCloud()
        viewModelScope.launch(Dispatchers.IO) {
            LiteRtLlmEngine.setBackend(BackendKind.from(modelPrefs.currentBackend()))
            LiteRtLlmEngine.warmUp(appContext)
            _uiState.update { it.copy(backend = LiteRtLlmEngine.currentBackend().name) }
        }
    }

    private fun refreshCloud() {
        val config = CloudConfigPrefs(appContext).load()
        _uiState.update {
            it.copy(isCloudEngine = config.usesCloud() && config.isComplete())
        }
    }

    private fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty()) return
        if (_uiState.value.isGenerating) {
            Log.w(TAG, "send blocked: isGenerating=true q=${question.take(20)}")
            return
        }
        Log.i(TAG, "send q=${question.take(40)}")
        appendMessage(
            ChatUiState.ChatMessage(role = ChatUiState.Role.USER, text = question)
        )
        synchronized(streamBuffer) { streamBuffer.setLength(0) }
        _uiState.update {
            it.copy(
                draft = "",
                isGenerating = true,
                streamingText = "",
                streamingThinking = "",
                modelMissing = false
            )
        }
        generationJob = viewModelScope.launch(Dispatchers.IO) {
            val ticker = launch {
                while (true) {
                    delay(STREAM_TICK_MS)
                    val snapshot = synchronized(streamBuffer) { streamBuffer.toString() }
                    val parsed = QaOrchestrator.parseAnswer(snapshot, fallbackToThinking = false)
                    _uiState.update { state ->
                        state.copy(
                            streamingText = parsed.answer,
                            streamingThinking = parsed.thinking
                        )
                    }
                }
            }
            val outcome = try {
                QaOrchestrator.ask(appContext, question) { delta ->
                    synchronized(streamBuffer) { streamBuffer.append(delta) }
                }
            } finally {
                ticker.cancel()
            }
            finish(outcome, synchronized(streamBuffer) { streamBuffer.toString() })
        }
    }

    private fun finish(outcome: QaOrchestrator.Outcome, generated: String) {
        synchronized(commitLock) {
            Log.i(
                TAG,
                "finish outcome=${outcome::class.simpleName} " +
                    "generatedLen=${generated.length} wasGenerating=${_uiState.value.isGenerating}"
            )
            if (!_uiState.value.isGenerating) return
            when (outcome) {
                QaOrchestrator.Outcome.Refusal -> appendMessage(
                    ChatUiState.ChatMessage(
                        role = ChatUiState.Role.REFUSAL,
                        text = appContext.getString(R.string.chat_refusal)
                    )
                )

                is QaOrchestrator.Outcome.ModelMissing -> {
                    _uiState.update { it.copy(modelMissing = true) }
                    appendMessage(excerptMessage(outcome.hits))
                    sendEffect(ChatEffect.ShowToast(R.string.chat_model_missing))
                }

                is QaOrchestrator.Outcome.Excerpt -> {
                    if (outcome.hits.isNotEmpty()) {
                        appendMessage(excerptMessage(outcome.hits))
                    }
                    sendEffect(ChatEffect.ShowToast(R.string.chat_generate_failed))
                }

                is QaOrchestrator.Outcome.Generated -> {
                    if (generated.isBlank()) {
                        if (outcome.hits.isNotEmpty()) {
                            appendMessage(excerptMessage(outcome.hits))
                        }
                        sendEffect(ChatEffect.ShowToast(R.string.chat_generate_failed))
                    } else {
                        val parsed = QaOrchestrator.parseDetailed(generated)
                        when {
                            parsed.refused -> appendMessage(
                                ChatUiState.ChatMessage(
                                    role = ChatUiState.Role.REFUSAL,
                                    text = appContext.getString(R.string.chat_refusal)
                                )
                            )

                            else -> appendMessage(
                                ChatUiState.ChatMessage(
                                    role = ChatUiState.Role.ASSISTANT,
                                    text = parsed.answer.ifBlank { generated },
                                    thinking = parsed.thinking,
                                    sources = outcome.hits.toSources()
                                )
                            )
                        }
                    }
                }
            }
            _uiState.update {
                it.copy(isGenerating = false, streamingText = "", streamingThinking = "")
            }
        }
    }

    private fun stop() {
        generationJob?.cancel()
        generationJob = null
        viewModelScope.launch(Dispatchers.IO) {
            LiteRtLlmEngine.stop()
        }
        synchronized(commitLock) {
            if (!_uiState.value.isGenerating) return
            val partial = synchronized(streamBuffer) { streamBuffer.toString() }
            if (partial.isNotBlank()) {
                val parsed = QaOrchestrator.parseDetailed(partial)
                appendMessage(
                    ChatUiState.ChatMessage(
                        role = ChatUiState.Role.ASSISTANT,
                        text = parsed.answer.ifBlank { partial },
                        thinking = parsed.thinking
                    )
                )
            }
            _uiState.update {
                it.copy(isGenerating = false, streamingText = "", streamingThinking = "")
            }
        }
    }

    private fun toggleVoiceInput() {
        when (_uiState.value.voicePhase) {
            ChatUiState.VoiceInputPhase.IDLE -> beginVoiceInput()
            ChatUiState.VoiceInputPhase.RECORDING -> stopVoiceRecording()
        }
    }

    private fun beginVoiceInput() {
        if (_uiState.value.isGenerating) return
        if (!StreamAsrModelManager.isReady(appContext)) {
            sendEffect(ChatEffect.ShowToast(R.string.chat_voice_model_missing))
            sendEffect(ChatEffect.OpenModelPage)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startVoiceRecording()
        } else {
            sendEffect(ChatEffect.RequestAudioPermission)
        }
    }

    private fun startVoiceRecording() {
        if (_uiState.value.voicePhase != ChatUiState.VoiceInputPhase.IDLE) return
        val recognizer = streamingAsr ?: StreamingSpeechRecognizer(
            StreamAsrModelManager.encoderFile(appContext),
            StreamAsrModelManager.decoderFile(appContext),
            StreamAsrModelManager.joinerFile(appContext),
            StreamAsrModelManager.tokensFile(appContext)
        ).also { streamingAsr = it }
        voiceBase = _uiState.value.draft
        voiceText = ""
        val started = recognizer.start(
            onPartial = { text -> publishVoiceText(text) },
            onError = { handleVoiceError() }
        )
        if (!started) {
            sendEffect(ChatEffect.ShowToast(R.string.voice_record_fail))
            return
        }
        _uiState.update { it.copy(voicePhase = ChatUiState.VoiceInputPhase.RECORDING) }
        voiceTimerJob = viewModelScope.launch {
            delay(VOICE_MAX_RECORD_MS)
            stopVoiceRecording()
        }
    }

    private fun publishVoiceText(text: String) {
        voiceText = text
        val base = voiceBase
        val merged = when {
            text.isBlank() -> base
            base.isBlank() -> text
            else -> base.trimEnd() + " " + text
        }
        _uiState.update { it.copy(draft = merged) }
    }

    private fun handleVoiceError() {
        voiceTimerJob?.cancel()
        voiceTimerJob = null
        _uiState.update { it.copy(voicePhase = ChatUiState.VoiceInputPhase.IDLE) }
        sendEffect(ChatEffect.ShowToast(R.string.voice_engine_fail))
    }

    private fun stopVoiceRecording() {
        if (_uiState.value.voicePhase != ChatUiState.VoiceInputPhase.RECORDING) return
        voiceTimerJob?.cancel()
        voiceTimerJob = null
        streamingAsr?.stop()
        _uiState.update { it.copy(voicePhase = ChatUiState.VoiceInputPhase.IDLE) }
        if (voiceText.isBlank()) {
            sendEffect(ChatEffect.ShowToast(R.string.chat_voice_empty))
        }
    }

    private fun clear() {
        generationJob?.cancel()
        generationJob = null
        synchronized(streamBuffer) { streamBuffer.setLength(0) }
        persistOps.trySend(PersistOp.ClearAll)
        _uiState.update {
            it.copy(
                messages = emptyList(),
                streamingText = "",
                streamingThinking = "",
                isGenerating = false,
                modelMissing = false
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            LiteRtLlmEngine.stop()
            LiteRtLlmEngine.resetConversation()
        }
    }

    private fun selectBackend(kind: BackendKind) {
        if (kind.name == _uiState.value.backend) return
        if (_uiState.value.isGenerating) stop()
        modelPrefs.setCurrentBackend(kind.name)
        _uiState.update { it.copy(backend = kind.name) }
        viewModelScope.launch(Dispatchers.IO) {
            LiteRtLlmEngine.setBackend(kind)
            LiteRtLlmEngine.warmUp(appContext)
            _uiState.update { it.copy(backend = LiteRtLlmEngine.currentBackend().name) }
        }
    }

    private fun excerptMessage(hits: List<RetrieveService.Hit>): ChatUiState.ChatMessage =
        ChatUiState.ChatMessage(
            role = ChatUiState.Role.ASSISTANT,
            text = QaOrchestrator.excerptText(appContext, hits),
            sources = hits.toSources()
        )

    private fun List<RetrieveService.Hit>.toSources(): List<ChatUiState.ChatSource> =
        distinctBy { it.noteId }.map { it.toSource() }

    private fun RetrieveService.Hit.toSource(): ChatUiState.ChatSource =
        ChatUiState.ChatSource(
            chunkId = chunkId,
            noteId = noteId,
            title = noteTitle,
            pageNo = pageNo
        )

    private fun appendMessage(message: ChatUiState.ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + message) }
        persistOps.trySend(PersistOp.Save(message))
    }

    private suspend fun restoreHistory(dao: QaLogDao) {
        val restored = dao.recent(HISTORY_LOAD).mapNotNull { it.toMessage() }.asReversed()
        if (restored.isEmpty()) return
        _uiState.update { state ->
            if (state.messages.isEmpty()) {
                state.copy(messages = restored)
            } else {
                state.copy(messages = restored + state.messages.filterNot { it in restored })
            }
        }
    }

    private fun ChatUiState.ChatMessage.toLog(): QaLogEntity =
        QaLogEntity(
            role = role.name,
            text = text,
            thinking = thinking,
            sourcesJson = encodeSources(sources),
            createdAt = System.currentTimeMillis()
        )

    private fun QaLogEntity.toMessage(): ChatUiState.ChatMessage? {
        val parsedRole = ChatUiState.Role.entries.firstOrNull { it.name == role } ?: return null
        return ChatUiState.ChatMessage(
            role = parsedRole,
            text = text,
            thinking = thinking,
            sources = decodeSources(sourcesJson)
        )
    }

    private fun encodeSources(sources: List<ChatUiState.ChatSource>): String {
        if (sources.isEmpty()) return ""
        val array = JSONArray()
        for (source in sources) {
            val item = JSONObject()
            item.put("chunk_id", source.chunkId)
            item.put("note_id", source.noteId)
            item.put("title", source.title)
            if (source.pageNo != null) item.put("page_no", source.pageNo)
            array.put(item)
        }
        return array.toString()
    }

    private fun decodeSources(raw: String): List<ChatUiState.ChatSource> {
        if (raw.isEmpty()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        ChatUiState.ChatSource(
                            chunkId = item.optInt("chunk_id", -1),
                            noteId = item.optString("note_id", ""),
                            title = item.optString("title", ""),
                            pageNo = if (item.has("page_no")) item.optInt("page_no") else null
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "decodeSources failed", e)
            emptyList()
        }
    }

    private fun lastQuestion(): String =
        _uiState.value.messages
            .lastOrNull { it.role == ChatUiState.Role.USER }
            ?.text
            .orEmpty()

    private fun sendEffect(effect: ChatEffect) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }

    override fun onCleared() {
        voiceTimerJob?.cancel()
        streamingAsr?.release()
        streamingAsr = null
    }

    private companion object {
        const val TAG = "ChatViewModel"
        const val STREAM_TICK_MS = 60L
        const val HISTORY_LOAD = 200
        const val HISTORY_KEEP = 500
        const val VOICE_MAX_RECORD_MS = 30_000L
    }
}
