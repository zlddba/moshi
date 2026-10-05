package dev.zlddba.moshiapp.activities.detailPage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.data.repo.IngestRepository
import dev.zlddba.moshiapp.domain.retrieve.RetrieveService
import dev.zlddba.moshiapp.domain.security.CryptoManager
import dev.zlddba.moshiapp.domain.summary.NoteSummarizer
import dev.zlddba.moshiapp.ingest.parse.IngestException
import dev.zlddba.moshiapp.ui.IngestMessages
import dev.zlddba.moshiapp.ui.doc.DocumentRenderer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

class DetailViewModel(context: Context) : ViewModel() {

    sealed interface DetailEvent {
        data class Init(
            val noteId: String?,
            val chunkId: Int,
            val keyword: String?
        ) : DetailEvent

        data object ToggleSensitive : DetailEvent
        data object Delete : DetailEvent
        data object StartEdit : DetailEvent
        data object CancelEdit : DetailEvent
        data class TitleChanged(val value: String) : DetailEvent
        data class BodyChanged(val value: String) : DetailEvent
        data class TagsChanged(val value: String) : DetailEvent
        data object SaveEdit : DetailEvent
        data object RegenerateSummary : DetailEvent
    }

    sealed interface DetailEffect {
        data class ShowToast(val messageRes: Int) : DetailEffect
    }

    private val appContext = context.applicationContext
    private val database = MoshiDatabase.get(appContext)

    private val _uiState = MutableStateFlow(sampleDetailUiState(context))
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _effects = Channel<DetailEffect>(Channel.BUFFERED)
    val effects: Flow<DetailEffect> = _effects.receiveAsFlow()

    private var initialized = false

    fun onEvent(event: DetailEvent) {
        when (event) {
            is DetailEvent.Init -> init(event)
            DetailEvent.ToggleSensitive -> toggleSensitive()
            DetailEvent.Delete -> deleteNote()
            DetailEvent.StartEdit -> _uiState.update { it.copy(isEditing = true) }
            DetailEvent.CancelEdit -> _uiState.update { it.copy(isEditing = false) }
            is DetailEvent.TitleChanged -> _uiState.update { it.copy(title = event.value) }
            is DetailEvent.BodyChanged -> _uiState.update { it.copy(bodyDraft = event.value) }
            is DetailEvent.TagsChanged -> _uiState.update { it.copy(tagsDraft = event.value) }
            DetailEvent.SaveEdit -> saveEdit()
            DetailEvent.RegenerateSummary -> regenerateSummary()
        }
    }

    private fun init(event: DetailEvent.Init) {
        if (initialized) return
        initialized = true
        val noteId = event.noteId?.takeUnless { it.isBlank() } ?: return
        viewModelScope.launch {
            val note = withContext(Dispatchers.IO) { database.noteDao().byId(noteId) }
            if (note == null) {
                _uiState.update { it.copy(missing = true) }
                return@launch
            }
            val chunks = withContext(Dispatchers.IO) { database.chunkDao().byNote(noteId) }
            val state = withContext(Dispatchers.IO) { buildState(note, chunks, event) }
            _uiState.update { state }
            loadRelated(note, chunks)
            ensureSummary(note)
        }
    }

    private suspend fun ensureSummary(note: NoteEntity) {
        if (note.summary.isNullOrBlank() && note.content.isNotBlank()) {
            val summary = withContext(Dispatchers.Default) {
                NoteSummarizer.summarize(note.content)
            }
            if (summary.isNotBlank()) {
                IngestRepository.updateNoteSummary(appContext, note.id, summary)
                _uiState.update { it.copy(abstract = summary) }
            }
        }
    }

    private fun buildState(
        note: NoteEntity,
        chunks: List<dev.zlddba.moshiapp.data.db.ChunkEntity>,
        event: DetailEvent.Init
    ): DetailUiState {
        val stored = note.sourceUri?.let { File(it) }?.takeIf { it.isFile }
        val kind = DocumentRenderer.kindOf(stored?.name ?: note.title, note.type, note.content)
        val file = stored?.let { CryptoManager.openForRead(appContext, it) }
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
            file?.let { add(appContext.getString(R.string.detail_source_file_fmt, it.name)) }
        }
        val focusIndex = chunks.indexOfFirst { it.id == event.chunkId }
        val focusChunk = chunks.getOrNull(focusIndex)
        return DetailUiState(
            noteId = note.id,
            isSensitive = note.isSensitive,
            isBuiltIn = note.isBuiltIn,
            title = note.title,
            abstract = note.summary.orEmpty(),
            tags = emptyList(),
            tagsDraft = "",
            heading = appContext.getString(R.string.detail_body_heading),
            bodyDraft = note.content,
            content = DetailUiState.ContentBlock(
                kind = kind,
                text = note.content,
                file = file,
                fileLabel = DocumentRenderer.labelOf(kind)
            ),
            paragraphs = chunks.map { DetailUiState.Paragraph(text = it.text, pageNo = it.pageNo) },
            sources = sources,
            highlight = event.keyword.orEmpty(),
            focusIndex = focusIndex,
            hitSnippet = focusChunk?.text.orEmpty(),
            hitRange = focusChunk?.let { it.charOffset until (it.charOffset + it.text.length) }
        )
    }

    private fun loadRelated(
        note: NoteEntity,
        chunks: List<dev.zlddba.moshiapp.data.db.ChunkEntity>
    ) {
        val queryText = note.content
            .ifBlank { chunks.joinToString(separator = "\n") { it.text } }
            .take(RELATED_QUERY_CHARS)
        if (queryText.isBlank()) return
        viewModelScope.launch {
            val related = withContext(Dispatchers.IO) {
                RetrieveService.relatedNotes(appContext, note.id, queryText)
            }
            _uiState.update { state ->
                state.copy(
                    related = related.map { item ->
                        DetailUiState.RelatedNote(
                            noteId = item.noteId,
                            title = item.title,
                            score = appContext.getString(
                                R.string.detail_related_score_fmt,
                                (item.similarity * 100).toInt().coerceIn(0, 100)
                            )
                        )
                    }
                )
            }
        }
        viewModelScope.launch {
            val tags = withContext(Dispatchers.IO) {
                IngestRepository.tagsOf(appContext, note.id)
            }
            _uiState.update { it.copy(tags = tags, tagsDraft = tags.joinToString(TAG_SEPARATOR)) }
        }
    }

    private fun toggleSensitive() {
        val noteId = _uiState.value.noteId
        if (noteId.isEmpty()) return
        val next = !_uiState.value.isSensitive
        _uiState.update { it.copy(isSensitive = next) }
        viewModelScope.launch(Dispatchers.IO) {
            database.noteDao().updateSensitive(noteId, next)
        }
    }

    private fun deleteNote() {
        val noteId = _uiState.value.noteId
        if (noteId.isEmpty() || _uiState.value.isBuiltIn || _deleted.value) return
        viewModelScope.launch {
            IngestRepository.deleteNote(appContext, noteId)
            _deleted.value = true
        }
    }

    private fun saveEdit() {
        val state = _uiState.value
        if (state.noteId.isEmpty()) return
        val title = state.title.trim()
        val body = state.bodyDraft
        if (title.isEmpty()) {
            sendEffect(DetailEffect.ShowToast(R.string.ingest_empty))
            return
        }
        if (body.isBlank()) {
            sendEffect(DetailEffect.ShowToast(R.string.ingest_empty))
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    IngestRepository.updateNoteTitle(appContext, state.noteId, title)
                    if (body != state.content.text) {
                        IngestRepository.updateNoteContent(appContext, state.noteId, body)
                    }
                    IngestRepository.setTags(appContext, state.noteId, state.tagsDraft)
                }
                val summary = withContext(Dispatchers.Default) { NoteSummarizer.summarize(body) }
                if (summary.isNotBlank()) {
                    IngestRepository.updateNoteSummary(appContext, state.noteId, summary)
                }
                val tags = withContext(Dispatchers.IO) {
                    IngestRepository.tagsOf(appContext, state.noteId)
                }
                _uiState.update {
                    it.copy(
                        isEditing = false,
                        tags = tags,
                        abstract = summary.ifBlank { it.abstract },
                        content = it.content.copy(text = body)
                    )
                }
                sendEffect(DetailEffect.ShowToast(R.string.detail_saved))
            } catch (e: IngestException) {
                sendEffect(DetailEffect.ShowToast(IngestMessages.errorOf(e.kind)))
            } catch (e: Exception) {
                sendEffect(DetailEffect.ShowToast(R.string.ingest_failed))
            }
        }
    }

    private fun regenerateSummary() {
        val state = _uiState.value
        if (state.noteId.isEmpty()) return
        viewModelScope.launch {
            val source = state.bodyDraft.ifBlank { state.content.text }
            val summary = withContext(Dispatchers.Default) { NoteSummarizer.summarize(source) }
            if (summary.isBlank()) return@launch
            IngestRepository.updateNoteSummary(appContext, state.noteId, summary)
            _uiState.update { it.copy(abstract = summary) }
            sendEffect(DetailEffect.ShowToast(R.string.detail_summary_updated))
        }
    }

    private fun typeLabel(type: String): String = when (type) {
        NoteEntity.TYPE_PDF -> appContext.getString(R.string.home_card_type_pdf)
        NoteEntity.TYPE_IMAGE_OCR -> appContext.getString(R.string.home_card_type_image)
        NoteEntity.TYPE_AUDIO -> appContext.getString(R.string.home_card_type_voice)
        NoteEntity.TYPE_SHEET -> appContext.getString(R.string.home_card_type_sheet)
        else -> appContext.getString(R.string.home_card_type_text)
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))

    private fun sendEffect(effect: DetailEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    companion object {
        private const val RELATED_QUERY_CHARS = 1200
        private const val TAG_SEPARATOR = ", "
    }
}
