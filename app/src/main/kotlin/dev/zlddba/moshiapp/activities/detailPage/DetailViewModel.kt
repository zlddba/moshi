package dev.zlddba.moshiapp.activities.detailPage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.db.ChunkEntity
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

class DetailViewModel(context: Context) : ViewModel() {

    sealed interface DetailEvent {
        data class Init(
            val noteId: String?,
            val chunkId: Int,
            val keyword: String?
        ) : DetailEvent

        data object ToggleSensitive : DetailEvent

        data object Delete : DetailEvent
    }

    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(sampleDetailUiState(context))
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private var initialized = false

    fun onEvent(event: DetailEvent) {
        when (event) {
            is DetailEvent.Init -> init(event)
            DetailEvent.ToggleSensitive -> toggleSensitive()
            DetailEvent.Delete -> deleteNote()
        }
    }

    private fun deleteNote() {
        val noteId = _uiState.value.noteId
        if (noteId.isEmpty() || _deleted.value) return
        viewModelScope.launch {
            IngestRepository.deleteNote(appContext, noteId)
            _deleted.value = true
        }
    }

    private fun toggleSensitive() {
        val noteId = _uiState.value.noteId
        if (noteId.isEmpty()) return
        val next = !_uiState.value.isSensitive
        _uiState.update { it.copy(isSensitive = next) }
        viewModelScope.launch(Dispatchers.IO) {
            MoshiDatabase.get(appContext).noteDao().updateSensitive(noteId, next)
        }
    }

    private fun init(event: DetailEvent.Init) {
        if (initialized) return
        initialized = true
        val noteId = event.noteId?.takeUnless { it.isBlank() } ?: return
        viewModelScope.launch {
            val database = MoshiDatabase.get(appContext)
            val note = withContext(Dispatchers.IO) {
                database.noteDao().byId(noteId)
            }
            if (note == null) {
                _uiState.update { it.copy(missing = true) }
                return@launch
            }
            val chunks = withContext(Dispatchers.IO) {
                database.chunkDao().byNote(noteId)
            }
            _uiState.update { buildState(note, chunks, event) }
        }
    }

    private fun buildState(
        note: NoteEntity,
        chunks: List<ChunkEntity>,
        event: DetailEvent.Init
    ): DetailUiState {
        val paragraphs = chunks.map { chunk ->
            DetailUiState.Paragraph(text = chunk.text, pageNo = chunk.pageNo)
        }
        val focusIndex = chunks.indexOfFirst { it.id == event.chunkId }
        val sources = buildList {
            add(appContext.getString(R.string.detail_source_type_fmt, typeLabel(note.type)))
            add(
                appContext.getString(
                    R.string.detail_source_time_fmt,
                    formatTime(note.createdAt)
                )
            )
            val sourceNote = note.sourceNote
            if (!sourceNote.isNullOrBlank()) {
                add(appContext.getString(R.string.detail_source_note_fmt, sourceNote))
            }
        }
        return DetailUiState(
            noteId = note.id,
            isSensitive = note.isSensitive,
            title = note.title,
            summary = note.summary.orEmpty(),
            heading = appContext.getString(R.string.detail_body_heading),
            paragraphs = paragraphs,
            sources = sources,
            highlight = event.keyword.orEmpty(),
            focusIndex = focusIndex
        )
    }

    private fun typeLabel(type: String): String = when (type) {
        NoteEntity.TYPE_PDF -> appContext.getString(R.string.home_card_type_pdf)
        NoteEntity.TYPE_IMAGE_OCR -> appContext.getString(R.string.home_card_type_image)
        NoteEntity.TYPE_AUDIO -> appContext.getString(R.string.home_card_type_voice)
        else -> appContext.getString(R.string.home_card_type_text)
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}
