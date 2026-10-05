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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainHomeViewModel(context: Context) : ViewModel() {

    sealed interface HomeEvent {
        data class FilterChanged(val index: Int) : HomeEvent
        data class TagSelected(val tagId: Int) : HomeEvent
        data class DeleteRequested(val noteId: String, val title: String) : HomeEvent
    }

    sealed interface HomeEffect {
        data class Deleted(val title: String) : HomeEffect
    }

    private val appContext = context.applicationContext
    private var notes: List<NoteEntity> = emptyList()
    private var filter = 0
    private var activeTagId = 0
    private var tagNames: Map<Int, String> = emptyMap()
    private var tagsByNote: Map<String, List<Int>> = emptyMap()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<HomeEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<HomeEffect> = _effects.asSharedFlow()

    init {
        refresh()
        viewModelScope.launch {
            merge(IngestRepository.savedNotes, IngestRepository.deletedNotes).collect {
                refresh()
            }
        }
        viewModelScope.launch {
            IngestRepository.libraryCleared.collect {
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

            is HomeEvent.TagSelected -> {
                activeTagId = if (activeTagId == event.tagId) 0 else event.tagId
                emit()
            }

            is HomeEvent.DeleteRequested -> deleteNote(event)
        }
    }

    private fun deleteNote(event: HomeEvent.DeleteRequested) {
        viewModelScope.launch {
            IngestRepository.deleteNote(appContext, event.noteId)
            _effects.tryEmit(HomeEffect.Deleted(event.title))
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val database = MoshiDatabase.get(appContext)
            val loaded = withContext(Dispatchers.IO) {
                val all = database.noteDao().recentAll()
                val tags = database.tagsDao().allTags()
                val links = database.tagsDao().allLinks()
                Triple(all, tags, links)
            }
            notes = loaded.first.filter { !it.isBuiltIn }
            tagNames = loaded.second.associate { it.id to it.name }
            tagsByNote = loaded.third.groupBy({ it.noteId }, { it.tagId })
            emit()
        }
    }

    private fun emit() {
        val chips = tagNames.entries
            .map { entry ->
                HomeUiState.TagChip(
                    id = entry.key,
                    name = entry.value,
                    refCount = tagsByNote.count { it.value.contains(entry.key) }
                )
            }
            .filter { it.refCount > 0 }
            .sortedWith(compareByDescending<HomeUiState.TagChip> { it.refCount }.thenBy { it.name })
        _uiState.update {
            HomeUiState(
                cards = sorted().map { note -> note.toCard() },
                filter = filter,
                loaded = true,
                tags = chips,
                activeTagId = activeTagId
            )
        }
    }

    private fun sorted(): List<NoteEntity> {
        val scoped = if (activeTagId == 0) {
            notes
        } else {
            notes.filter { tagsByNote[it.id]?.contains(activeTagId) == true }
        }
        return when (filter) {
            1 -> scoped.sortedWith(
                compareBy<NoteEntity> { it.type }.thenByDescending { it.createdAt }
            )

            else -> scoped
        }
    }

    private fun NoteEntity.toCard(): HomeUiState.NoteCard {
        val preview = summary?.takeIf { it.isNotBlank() }
            ?: content.replace('\n', ' ').take(80)
        val names = tagsByNote[id].orEmpty().mapNotNull { tagNames[it] }
        return HomeUiState.NoteCard(
            noteId = id,
            type = type,
            title = title,
            summary = preview,
            tag = sourceNote.orEmpty(),
            time = formatTime(createdAt),
            tags = names
        )
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}
