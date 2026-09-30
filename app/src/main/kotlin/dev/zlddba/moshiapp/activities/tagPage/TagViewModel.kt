package dev.zlddba.moshiapp.activities.tagPage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.repo.IngestRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TagViewModel(context: Context) : ViewModel() {

    data class TagRow(
        val id: Int,
        val name: String,
        val refCount: Int
    )

    data class TagUiState(
        val tags: List<TagRow> = emptyList(),
        val loaded: Boolean = false
    )

    sealed interface TagEvent {
        data object Refresh : TagEvent
        data class Rename(val id: Int, val name: String) : TagEvent
        data class Delete(val id: Int) : TagEvent
    }

    sealed interface TagEffect {
        data class ShowToast(val messageRes: Int) : TagEffect
    }

    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(TagUiState())
    val uiState: StateFlow<TagUiState> = _uiState.asStateFlow()

    private val _effects = Channel<TagEffect>(Channel.BUFFERED)
    val effects: Flow<TagEffect> = _effects.receiveAsFlow()

    init {
        refresh()
    }

    fun onEvent(event: TagEvent) {
        when (event) {
            TagEvent.Refresh -> refresh()
            is TagEvent.Rename -> rename(event.id, event.name)
            is TagEvent.Delete -> delete(event.id)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) {
                IngestRepository.listTags(appContext)
            }
            _uiState.update {
                it.copy(
                    tags = rows.map { info ->
                        TagRow(id = info.id, name = info.name, refCount = info.refCount)
                    },
                    loaded = true
                )
            }
        }
    }

    private fun rename(id: Int, name: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                IngestRepository.renameTag(appContext, id, name)
            }
            if (ok) {
                sendEffect(TagEffect.ShowToast(R.string.tag_saved))
                refresh()
            } else {
                sendEffect(TagEffect.ShowToast(R.string.ingest_empty))
            }
        }
    }

    private fun delete(id: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { IngestRepository.deleteTag(appContext, id) }
            refresh()
        }
    }

    private fun sendEffect(effect: TagEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
