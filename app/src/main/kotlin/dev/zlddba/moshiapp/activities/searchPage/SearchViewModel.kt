package dev.zlddba.moshiapp.activities.searchPage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.domain.retrieve.RetrieveService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(context: Context) : ViewModel() {

    data class SearchUiState(
        val query: String = "",
        val isSearching: Boolean = false,
        val searched: Boolean = false,
        val results: List<SearchResult> = emptyList()
    ) {
        data class SearchResult(
            val chunkId: Int,
            val noteId: String,
            val noteTitle: String,
            val text: String,
            val pageNo: Int?,
            val score: Float
        )
    }

    sealed interface SearchEvent {
        data class Init(val query: String) : SearchEvent
        data class QueryChanged(val query: String) : SearchEvent
        data object Submit : SearchEvent
        data class ItemClick(val result: SearchUiState.SearchResult) : SearchEvent
    }

    sealed interface SearchEffect {
        data class OpenDetail(
            val noteId: String,
            val chunkId: Int,
            val keyword: String
        ) : SearchEffect
    }

    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _effects = Channel<SearchEffect>(Channel.BUFFERED)
    val effects: Flow<SearchEffect> = _effects.receiveAsFlow()

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.Init -> {
                if (event.query.isEmpty() || event.query == _uiState.value.query) return
                _uiState.update { it.copy(query = event.query) }
                search()
            }

            is SearchEvent.QueryChanged -> _uiState.update { it.copy(query = event.query) }

            SearchEvent.Submit -> search()

            is SearchEvent.ItemClick -> sendEffect(
                SearchEffect.OpenDetail(
                    noteId = event.result.noteId,
                    chunkId = event.result.chunkId,
                    keyword = _uiState.value.query.trim()
                )
            )
        }
    }

    private fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty() || _uiState.value.isSearching) return
        _uiState.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            try {
                val hits = RetrieveService.retrieve(appContext, query, SEARCH_TOP_K)
                _uiState.update { state ->
                    state.copy(
                        isSearching = false,
                        searched = true,
                        results = hits.map { hit ->
                            SearchUiState.SearchResult(
                                chunkId = hit.chunkId,
                                noteId = hit.noteId,
                                noteTitle = hit.noteTitle,
                                text = hit.text,
                                pageNo = hit.pageNo,
                                score = hit.score
                            )
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSearching = false, searched = true, results = emptyList())
                }
            }
        }
    }

    private fun sendEffect(effect: SearchEffect) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }

    private companion object {
        const val SEARCH_TOP_K = 10
    }
}
