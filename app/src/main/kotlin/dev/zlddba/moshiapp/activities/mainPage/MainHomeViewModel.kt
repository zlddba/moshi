package dev.zlddba.moshiapp.activities.mainPage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.data.repo.IngestRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainHomeViewModel(context: Context) : ViewModel() {

    sealed interface HomeEvent {
        data class FilterChanged(val index: Int) : HomeEvent
    }

    private val appContext = context.applicationContext
    private var notes: List<NoteEntity> = emptyList()
    private var filter = 0

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            IngestRepository.savedNotes.collect {
                refresh()
            }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.FilterChanged -> {
                filter = event.index
                emit()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                MoshiDatabase.get(appContext).noteDao().recentAll()
            }
            notes = loaded
            emit()
        }
    }

    private fun emit() {
        _uiState.update {
            HomeUiState(
                cards = sorted().map { note -> note.toCard() },
                filter = filter,
                loaded = true
            )
        }
    }

    private fun sorted(): List<NoteEntity> = when (filter) {
        1 -> notes.sortedWith(
            compareBy<NoteEntity> { it.type }.thenByDescending { it.createdAt }
        )

        else -> notes
    }

    private fun NoteEntity.toCard(): HomeUiState.NoteCard {
        val preview = summary?.takeIf { it.isNotBlank() }
            ?: content.replace('\n', ' ').take(80)
        return HomeUiState.NoteCard(
            noteId = id,
            type = type,
            title = title,
            summary = preview,
            tag = sourceNote.orEmpty(),
            time = formatTime(createdAt)
        )
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}
