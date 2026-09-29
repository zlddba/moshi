package dev.zlddba.moshiapp.activities.mainPage

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.prefs.CloudConfigPrefs
import dev.zlddba.moshiapp.data.prefs.ModelPrefs
import dev.zlddba.moshiapp.domain.qa.QaOrchestrator
import dev.zlddba.moshiapp.domain.retrieve.RetrieveService
import dev.zlddba.moshiapp.engine.local.BackendKind
import dev.zlddba.moshiapp.engine.local.LiteRtLlmEngine
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

class ChatViewModel(context: Context) : ViewModel() {

    data class ChatUiState(
        val isCloudEngine: Boolean = false,
        val backend: String = BackendKind.CPU.name,
        val messages: List<ChatMessage> = emptyList(),
        val streamingText: String = "",
        val streamingThinking: String = "",
        val isGenerating: Boolean = false,
        val modelMissing: Boolean = false
    ) {
        enum class Role { USER, ASSISTANT, REFUSAL }

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
    }

    sealed interface ChatEffect {
        data class OpenDetail(
            val noteId: String,
            val chunkId: Int,
            val keyword: String
        ) : ChatEffect

        data object OpenModelPage : ChatEffect
        data class ShowToast(val messageRes: Int) : ChatEffect
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
                    val parsed = QaOrchestrator.parseAnswer(snapshot)
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
                        val parsed = QaOrchestrator.parseAnswer(generated)
                        val answer = parsed.answer.ifBlank { generated }
                        if (isInsufficient(answer)) {
                            appendMessage(
                                ChatUiState.ChatMessage(
                                    role = ChatUiState.Role.REFUSAL,
                                    text = appContext.getString(R.string.chat_refusal)
                                )
                            )
                        } else {
                            appendMessage(
                                ChatUiState.ChatMessage(
                                    role = ChatUiState.Role.ASSISTANT,
                                    text = answer,
                                    thinking = parsed.thinking,
                                    sources = outcome.hits.map { it.toSource() }
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
                val parsed = QaOrchestrator.parseAnswer(partial)
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

    private fun clear() {
        generationJob?.cancel()
        generationJob = null
        synchronized(streamBuffer) { streamBuffer.setLength(0) }
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
            sources = hits.map { it.toSource() }
        )

    private fun isInsufficient(answer: String): Boolean =
        answer.contains(appContext.getString(R.string.chat_refusal)) ||
            answer.contains("资料不足") ||
            answer.contains("没有找到相关内容") ||
            answer.contains("未找到相关内容")

    private fun RetrieveService.Hit.toSource(): ChatUiState.ChatSource =
        ChatUiState.ChatSource(
            chunkId = chunkId,
            noteId = noteId,
            title = noteTitle,
            pageNo = pageNo
        )

    private fun appendMessage(message: ChatUiState.ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + message) }
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

    private companion object {
        const val TAG = "ChatViewModel"
        const val STREAM_TICK_MS = 60L
    }
}
