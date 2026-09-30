package dev.zlddba.moshiapp.activities.textPage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.data.repo.IngestRepository
import dev.zlddba.moshiapp.domain.title.TitleSuggester
import dev.zlddba.moshiapp.ingest.parse.IngestException
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

class TextViewModel(context: Context) : ViewModel() {

    data class TextUiState(
        val content: String = "",
        val title: String = "",
        val isPreview: Boolean = false,
        val isGeneratingTitle: Boolean = false,
        val tags: String = ""
    )

    sealed interface TextEvent {
        data class ContentChanged(val value: String) : TextEvent
        data class TitleChanged(val value: String) : TextEvent
        data class TagsChanged(val value: String) : TextEvent
        data class PasteText(val text: String) : TextEvent
        data class ModeChanged(val preview: Boolean) : TextEvent
        data object RegenerateTitle : TextEvent
        data object ConfirmClicked : TextEvent
    }

    sealed interface TextEffect {
        data class ShowToast(val messageRes: Int) : TextEffect
        data object Close : TextEffect
    }

    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(TextUiState())
    val uiState: StateFlow<TextUiState> = _uiState.asStateFlow()

    private val _effects = Channel<TextEffect>(Channel.BUFFERED)
    val effects: Flow<TextEffect> = _effects.receiveAsFlow()

    fun onEvent(event: TextEvent) {
        when (event) {
            is TextEvent.ContentChanged -> _uiState.update { it.copy(content = event.value) }
            is TextEvent.TitleChanged -> _uiState.update { it.copy(title = event.value) }
            is TextEvent.TagsChanged -> _uiState.update { it.copy(tags = event.value) }
            is TextEvent.PasteText -> _uiState.update {
                val separator = if (it.content.isEmpty()) "" else "\n"
                it.copy(content = it.content + separator + event.text, isPreview = false)
            }

            is TextEvent.ModeChanged -> _uiState.update { it.copy(isPreview = event.preview) }
            TextEvent.RegenerateTitle -> generateTitle()
            TextEvent.ConfirmClicked -> confirm()
        }
    }

    private fun generateTitle() {
        val content = _uiState.value.content
        if (content.isBlank()) {
            sendEffect(TextEffect.ShowToast(R.string.ingest_empty))
            return
        }
        if (_uiState.value.isGeneratingTitle) return
        _uiState.update { it.copy(isGeneratingTitle = true) }
        viewModelScope.launch {
            val suggestion = TitleSuggester.suggest(appContext, content)
            _uiState.update { it.copy(title = suggestion.title, isGeneratingTitle = false) }
            if (!suggestion.fromModel) {
                sendEffect(TextEffect.ShowToast(R.string.ingest_title_failed))
            }
        }
    }

    private fun confirm() {
        val state = _uiState.value
        if (state.content.isBlank()) {
            sendEffect(TextEffect.ShowToast(R.string.ingest_empty))
            return
        }
        viewModelScope.launch {
            try {
                val result = IngestRepository.importText(
                    context = appContext,
                    text = state.content,
                    type = NoteEntity.TYPE_TEXT,
                    fallbackTitle = appContext.getString(R.string.text_title),
                    title = state.title
                )
                if (state.tags.isNotBlank()) {
                    IngestRepository.setTags(appContext, result.noteId, state.tags)
                }
                sendEffect(TextEffect.ShowToast(R.string.ingest_success))
                sendEffect(TextEffect.Close)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IngestException) {
                sendEffect(TextEffect.ShowToast(IngestMessages.errorOf(e.kind)))
            } catch (e: Exception) {
                sendEffect(TextEffect.ShowToast(R.string.ingest_failed))
            }
        }
    }

    private fun sendEffect(effect: TextEffect) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }
}
