package dev.zlddba.moshiapp.activities.privacyPage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.prefs.ModelPrefs
import dev.zlddba.moshiapp.domain.index.IndexOrchestrator
import dev.zlddba.moshiapp.ingest.models.ModelCatalog
import dev.zlddba.moshiapp.ingest.models.ModelFileManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ModelViewModel(context: Context) : ViewModel() {

    data class ModelCardState(
        val ready: Boolean = false,
        val downloading: Boolean = false,
        val progress: Int = 0,
        val bytesOnDisk: Long = 0L
    )

    data class ModelUiState(
        val gemma: ModelCardState = ModelCardState(),
        val qwen: ModelCardState = ModelCardState(),
        val gecko: ModelCardState = ModelCardState(),
        val asr: ModelCardState = ModelCardState(),
        val currentLlm: String = ModelCatalog.GEMMA
    )

    sealed interface ModelEvent {
        data class Download(val id: String) : ModelEvent
        data class Cancel(val id: String) : ModelEvent
        data class Delete(val id: String) : ModelEvent
        data class SwitchLlm(val id: String) : ModelEvent
    }

    sealed interface ModelEffect {
        data class ShowToast(val messageRes: Int, val argRes: Int? = null) : ModelEffect
    }

    private val appContext = context.applicationContext
    private val modelPrefs = ModelPrefs(appContext)
    private var downloadJob: Job? = null
    private var downloadSeq = 0

    private val _modelUiState = MutableStateFlow(initialState())
    val modelUiState: StateFlow<ModelUiState> = _modelUiState.asStateFlow()

    private val _effects = Channel<ModelEffect>(Channel.BUFFERED)
    val effects: Flow<ModelEffect> = _effects.receiveAsFlow()

    fun onEvent(event: ModelEvent) {
        when (event) {
            is ModelEvent.Download -> startDownload(event.id)
            is ModelEvent.Cancel -> cancelDownload(event.id)
            is ModelEvent.Delete -> deleteModel(event.id)
            is ModelEvent.SwitchLlm -> switchLlm(event.id)
        }
    }

    private fun initialState(): ModelUiState = ModelUiState(
        gemma = cardStateOf(ModelCatalog.GEMMA),
        qwen = cardStateOf(ModelCatalog.QWEN),
        gecko = cardStateOf(ModelCatalog.GECKO),
        asr = cardStateOf(ModelCatalog.SENSE_VOICE),
        currentLlm = modelPrefs.currentLlm()
    )

    private fun cardStateOf(id: String): ModelCardState {
        val ready = ModelFileManager.isReady(appContext, id)
        return ModelCardState(
            ready = ready,
            bytesOnDisk = if (ready) ModelFileManager.bytesOnDisk(appContext, id) else 0L
        )
    }

    private fun startDownload(id: String) {
        if (downloadJob != null) {
            sendEffect(ModelEffect.ShowToast(R.string.model_download_busy))
            return
        }
        val card = _modelUiState.value.card(id)
        if (card.ready || card.downloading) return
        _modelUiState.update { it.withCard(id, card.copy(downloading = true, progress = 0)) }
        val seq = ++downloadSeq
        val job = viewModelScope.launch {
            try {
                ModelFileManager.download(appContext, id) { transferred ->
                    val percent = ((transferred * 100) / ModelFileManager.totalBytes(id))
                        .toInt()
                        .coerceIn(0, 100)
                    _modelUiState.update { state ->
                        val current = state.card(id)
                        if (current.progress == percent) state
                        else state.withCard(id, current.copy(progress = percent))
                    }
                }
                _modelUiState.update {
                    it.withCard(
                        id,
                        ModelCardState(
                            ready = true,
                            progress = 100,
                            bytesOnDisk = ModelFileManager.bytesOnDisk(appContext, id)
                        )
                    )
                }
                if (id == ModelCatalog.GECKO) {
                    IndexOrchestrator.requestSweep()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _modelUiState.update { it.withCard(id, cardStateOf(id)) }
                sendEffect(ModelEffect.ShowToast(R.string.model_download_fail))
            } finally {
                if (downloadSeq == seq) downloadJob = null
            }
        }
        downloadJob = job
    }

    private fun cancelDownload(id: String) {
        if (downloadJob == null) return
        val card = _modelUiState.value.card(id)
        if (!card.downloading) return
        downloadSeq++
        val job = downloadJob
        downloadJob = null
        job?.cancel()
        _modelUiState.update { it.withCard(id, cardStateOf(id)) }
    }

    private fun deleteModel(id: String) {
        val state = _modelUiState.value
        val card = state.card(id)
        if (!card.ready || card.downloading) return
        viewModelScope.launch {
            ModelFileManager.delete(appContext, id)
            val latest = _modelUiState.value
            val currentLlm = if (
                id in ModelCatalog.LLM_IDS && latest.currentLlm == id
            ) {
                val other = ModelCatalog.LLM_IDS.first { it != id }
                if (ModelFileManager.isReady(appContext, other)) {
                    modelPrefs.setCurrentLlm(other)
                    other
                } else {
                    id
                }
            } else {
                latest.currentLlm
            }
            _modelUiState.update {
                it.withCard(id, ModelCardState()).copy(currentLlm = currentLlm)
            }
            sendEffect(ModelEffect.ShowToast(R.string.model_deleted, nameResOf(id)))
        }
    }

    private fun switchLlm(id: String) {
        val state = _modelUiState.value
        if (id !in ModelCatalog.LLM_IDS) return
        val card = state.card(id)
        if (!card.ready || card.downloading || state.currentLlm == id) return
        modelPrefs.setCurrentLlm(id)
        _modelUiState.update { it.copy(currentLlm = id) }
        sendEffect(ModelEffect.ShowToast(R.string.model_switched, nameResOf(id)))
    }

    private fun nameResOf(id: String): Int = when (id) {
        ModelCatalog.GEMMA -> R.string.model_name_gemma
        ModelCatalog.QWEN -> R.string.model_name_qwen
        ModelCatalog.GECKO -> R.string.model_name_embed
        else -> R.string.model_name_asr
    }

    private fun ModelUiState.card(id: String): ModelCardState = when (id) {
        ModelCatalog.GEMMA -> gemma
        ModelCatalog.QWEN -> qwen
        ModelCatalog.GECKO -> gecko
        else -> asr
    }

    private fun ModelUiState.withCard(id: String, card: ModelCardState): ModelUiState = when (id) {
        ModelCatalog.GEMMA -> copy(gemma = card)
        ModelCatalog.QWEN -> copy(qwen = card)
        ModelCatalog.GECKO -> copy(gecko = card)
        else -> copy(asr = card)
    }

    private fun sendEffect(effect: ModelEffect) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }
}
