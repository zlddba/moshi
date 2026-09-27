package dev.zlddba.moshiapp.activities.privacyPage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.ingest.vision.AsrModelManager
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

    data class ModelUiState(
        val asrReady: Boolean = false,
        val asrDownloading: Boolean = false,
        val asrProgress: Int = 0
    )

    sealed interface ModelEvent {
        data object AsrDownloadClicked : ModelEvent
        data object AsrCancelClicked : ModelEvent
        data object AsrDeleteClicked : ModelEvent
    }

    sealed interface ModelEffect {
        data class ShowToast(val messageRes: Int) : ModelEffect
    }

    private val appContext = context.applicationContext
    private var downloadJob: Job? = null

    private val _modelUiState = MutableStateFlow(
        ModelUiState(asrReady = AsrModelManager.isReady(appContext))
    )
    val modelUiState: StateFlow<ModelUiState> = _modelUiState.asStateFlow()

    private val _effects = Channel<ModelEffect>(Channel.BUFFERED)
    val effects: Flow<ModelEffect> = _effects.receiveAsFlow()

    fun onEvent(event: ModelEvent) {
        when (event) {
            ModelEvent.AsrDownloadClicked -> startDownload()
            ModelEvent.AsrCancelClicked -> cancelDownload()
            ModelEvent.AsrDeleteClicked -> deleteModel()
        }
    }

    private fun startDownload() {
        val state = _modelUiState.value
        if (state.asrReady || state.asrDownloading) return
        _modelUiState.update { it.copy(asrDownloading = true, asrProgress = 0) }
        downloadJob = viewModelScope.launch {
            try {
                AsrModelManager.download(appContext) { transferred ->
                    val percent = ((transferred * 100) / AsrModelManager.totalBytes())
                        .toInt()
                        .coerceIn(0, 100)
                    _modelUiState.update { current ->
                        if (current.asrProgress == percent) current
                        else current.copy(asrProgress = percent)
                    }
                }
                _modelUiState.update {
                    it.copy(asrReady = true, asrDownloading = false, asrProgress = 100)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _modelUiState.update { it.copy(asrDownloading = false, asrProgress = 0) }
                sendEffect(ModelEffect.ShowToast(R.string.model_download_fail))
            } finally {
                downloadJob = null
            }
        }
    }

    private fun cancelDownload() {
        if (!_modelUiState.value.asrDownloading) return
        val job = downloadJob
        downloadJob = null
        job?.cancel()
        _modelUiState.update {
            it.copy(
                asrDownloading = false,
                asrProgress = 0,
                asrReady = AsrModelManager.isReady(appContext)
            )
        }
    }

    private fun deleteModel() {
        val state = _modelUiState.value
        if (!state.asrReady || state.asrDownloading) return
        viewModelScope.launch {
            AsrModelManager.delete(appContext)
            _modelUiState.update { it.copy(asrReady = false, asrProgress = 0) }
        }
    }

    private fun sendEffect(effect: ModelEffect) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }
}
