package dev.zlddba.moshiapp.activities.ocrPage

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.data.repo.IngestRepository
import dev.zlddba.moshiapp.domain.title.TitleSuggester
import dev.zlddba.moshiapp.ingest.parse.IngestException
import dev.zlddba.moshiapp.ingest.vision.OcrTextRecognizer
import dev.zlddba.moshiapp.ui.IngestMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OcrViewModel(context: Context) : ViewModel() {

    data class OcrUiState(
        val imageUri: String? = null,
        val text: String = "",
        val title: String = "",
        val tags: String = "",
        val sourceNote: String = "",
        val isRecognizing: Boolean = false,
        val isGeneratingTitle: Boolean = false,
        val recognized: Boolean = false
    )

    sealed interface OcrEvent {
        data class Init(val imageUri: String?) : OcrEvent
        data class TextChanged(val text: String) : OcrEvent
        data class TitleChanged(val value: String) : OcrEvent
        data class TagsChanged(val value: String) : OcrEvent
        data class SourceNoteChanged(val value: String) : OcrEvent
        data object RecognizeAgain : OcrEvent
        data object RegenerateTitle : OcrEvent
        data object RecaptureClicked : OcrEvent
        data object ConfirmClicked : OcrEvent
    }

    sealed interface OcrEffect {
        data class ShowToast(val messageRes: Int) : OcrEffect
        data object Close : OcrEffect
        data object CloseWithResult : OcrEffect
    }

    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private val _effects = Channel<OcrEffect>(Channel.BUFFERED)
    val effects: Flow<OcrEffect> = _effects.receiveAsFlow()

    fun onEvent(event: OcrEvent) {
        when (event) {
            is OcrEvent.Init -> {
                val uri = event.imageUri
                if (uri.isNullOrBlank() || uri == _uiState.value.imageUri) return
                _uiState.update { it.copy(imageUri = uri, recognized = false) }
                recognize(autoTitle = true)
            }

            is OcrEvent.TextChanged -> _uiState.update { it.copy(text = event.text) }
            is OcrEvent.TitleChanged -> _uiState.update { it.copy(title = event.value) }
            is OcrEvent.TagsChanged -> _uiState.update { it.copy(tags = event.value) }
            is OcrEvent.SourceNoteChanged -> _uiState.update { it.copy(sourceNote = event.value) }
            OcrEvent.RecognizeAgain -> recognize(autoTitle = false)
            OcrEvent.RegenerateTitle -> generateTitle()
            OcrEvent.RecaptureClicked -> sendEffect(OcrEffect.CloseWithResult)
            OcrEvent.ConfirmClicked -> confirm()
        }
    }

    private fun recognize(autoTitle: Boolean) {
        val uri = _uiState.value.imageUri ?: return
        if (_uiState.value.isRecognizing) return
        _uiState.update { it.copy(isRecognizing = true) }
        viewModelScope.launch {
            try {
                val result = OcrTextRecognizer.recognize(appContext, Uri.parse(uri))
                _uiState.update {
                    it.copy(isRecognizing = false, text = result, recognized = true)
                }
                if (result.isBlank()) {
                    sendEffect(OcrEffect.ShowToast(R.string.ocr_fail))
                    return@launch
                }
                if (autoTitle && _uiState.value.title.isBlank()) generateTitle()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecognizing = false) }
                sendEffect(OcrEffect.ShowToast(R.string.ocr_fail))
            }
        }
    }

    private fun generateTitle() {
        val content = _uiState.value.text
        if (content.isBlank()) return
        if (_uiState.value.isGeneratingTitle) return
        _uiState.update { it.copy(isGeneratingTitle = true) }
        viewModelScope.launch {
            val suggestion = TitleSuggester.suggest(appContext, content)
            _uiState.update { it.copy(title = suggestion.title, isGeneratingTitle = false) }
        }
    }

    private fun confirm() {
        val state = _uiState.value
        if (state.text.isBlank()) {
            sendEffect(OcrEffect.ShowToast(R.string.ingest_empty))
            return
        }
        viewModelScope.launch {
            try {
                val result = IngestRepository.importText(
                    context = appContext,
                    text = state.text,
                    type = NoteEntity.TYPE_IMAGE_OCR,
                    fallbackTitle = appContext.getString(R.string.ocr_title),
                    title = state.title,
                    sourceNote = state.sourceNote.ifBlank { null },
                    sourceUri = state.imageUri?.let(Uri::parse)
                )
                if (state.tags.isNotBlank()) {
                    IngestRepository.setTags(appContext, result.noteId, state.tags)
                }
                sendEffect(OcrEffect.ShowToast(R.string.ingest_success))
                sendEffect(OcrEffect.Close)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IngestException) {
                sendEffect(OcrEffect.ShowToast(IngestMessages.errorOf(e.kind)))
            } catch (e: Exception) {
                sendEffect(OcrEffect.ShowToast(R.string.ingest_failed))
            }
        }
    }

    private fun sendEffect(effect: OcrEffect) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }
}
