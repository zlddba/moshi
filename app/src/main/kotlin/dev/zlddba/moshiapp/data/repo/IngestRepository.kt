package dev.zlddba.moshiapp.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room3.withWriteTransaction
import dev.zlddba.moshiapp.data.db.ChunkEntity
import dev.zlddba.moshiapp.data.db.KeywordIndex
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.data.vector.VectorStoreClient
import dev.zlddba.moshiapp.domain.chunk.Chunker
import dev.zlddba.moshiapp.ingest.parse.IngestException
import dev.zlddba.moshiapp.ingest.parse.ParsedDoc
import dev.zlddba.moshiapp.ingest.parse.ParserRegistry
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

object IngestRepository {

    const val MAX_FILE_BYTES = 20L * 1024 * 1024

    private val _savedNotes = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val savedNotes: SharedFlow<String> = _savedNotes.asSharedFlow()

    private val _deletedNotes = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val deletedNotes: SharedFlow<String> = _deletedNotes.asSharedFlow()

    sealed interface Stage {
        data object Parsing : Stage
        data object Chunking : Stage
        data object Writing : Stage
        data class Ocring(val page: Int, val total: Int) : Stage
    }

    data class Summary(
        val noteId: String,
        val chunkCount: Int
    )

    suspend fun importFile(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String?,
        onStage: (Stage) -> Unit
    ): Summary = withContext(Dispatchers.IO) {
        checkSize(context, uri)
        onStage(Stage.Parsing)
        val parser = ParserRegistry.resolve(fileName, mimeType)
            ?: throw IngestException(IngestException.Kind.UNSUPPORTED)
        val parsed = parser.parse(context, uri, fileName) { page, total ->
            onStage(Stage.Ocring(page, total))
        }
        onStage(Stage.Chunking)
        val chunks = Chunker.chunk(parsed.text)
        if (chunks.isEmpty()) throw IngestException(IngestException.Kind.EMPTY)
        onStage(Stage.Writing)
        val noteId = UUID.randomUUID().toString()
        val localFile = try {
            copyOriginal(context, uri, noteId, parsed.format)
        } catch (e: IOException) {
            null
        }
        persist(
            context = context,
            noteId = noteId,
            title = parsed.title,
            content = parsed.text,
            type = if (parsed.format == ParsedDoc.FORMAT_PDF) {
                NoteEntity.TYPE_PDF
            } else {
                NoteEntity.TYPE_TEXT
            },
            pageOffsets = parsed.pageOffsets,
            chunks = chunks,
            sourceUri = localFile?.path ?: uri.toString()
        )
    }

    suspend fun importText(
        context: Context,
        text: String,
        type: String,
        fallbackTitle: String,
        sourceNote: String? = null,
        sourceUri: Uri? = null
    ): Summary = withContext(Dispatchers.IO) {
        val content = text.trim()
        if (content.isEmpty()) throw IngestException(IngestException.Kind.EMPTY)
        val chunks = Chunker.chunk(content)
        if (chunks.isEmpty()) throw IngestException(IngestException.Kind.EMPTY)
        val noteId = UUID.randomUUID().toString()
        val storedSource = sourceUri?.let { uri ->
            try {
                copyOriginal(context, uri, noteId, sourceFormatOf(type)).path
            } catch (e: IOException) {
                uri.toString()
            }
        }
        persist(
            context = context,
            noteId = noteId,
            title = titleOf(content, fallbackTitle),
            content = content,
            type = type,
            pageOffsets = emptyList(),
            chunks = chunks,
            sourceUri = storedSource,
            sourceNote = sourceNote
        )
    }

    suspend fun deleteNote(context: Context, noteId: String) {
        withContext(Dispatchers.IO) {
            val database = MoshiDatabase.get(context)
            val note = database.noteDao().byId(noteId) ?: return@withContext
            database.withWriteTransaction {
                database.chunkDao().deleteByNote(noteId)
                database.noteDao().deleteById(noteId)
            }
            KeywordIndex.deleteNote(context, noteId)
            VectorStoreClient.deleteByNote(context, noteId)
            deleteStoredSource(context, note.sourceUri)
            _deletedNotes.tryEmit(noteId)
        }
    }

    private fun deleteStoredSource(context: Context, sourceUri: String?) {
        if (sourceUri.isNullOrBlank()) return
        try {
            val notesDir = File(context.filesDir, "notes")
            val file = File(sourceUri)
            if (file.parentFile?.canonicalPath == notesDir.canonicalPath) {
                file.delete()
            }
        } catch (e: Throwable) {
        }
    }

    private fun sourceFormatOf(type: String): String = when (type) {
        NoteEntity.TYPE_AUDIO -> "wav"
        NoteEntity.TYPE_IMAGE_OCR -> "img"
        else -> "bin"
    }

    private suspend fun persist(
        context: Context,
        noteId: String,
        title: String,
        content: String,
        type: String,
        pageOffsets: List<Int>,
        chunks: List<Chunker.TextChunk>,
        sourceUri: String?,
        sourceNote: String? = null
    ): Summary {
        val database = MoshiDatabase.get(context)
        val now = System.currentTimeMillis()
        val note = NoteEntity(
            id = noteId,
            type = type,
            title = title,
            content = content,
            sourceUri = sourceUri,
            sourceNote = sourceNote,
            indexStatus = NoteEntity.STATUS_PENDING,
            createdAt = now,
            updatedAt = now
        )
        val entities = chunks.map { chunk ->
            ChunkEntity(
                noteId = noteId,
                seq = chunk.seq,
                text = chunk.text,
                charOffset = chunk.offset,
                pageNo = pageOf(pageOffsets, chunk.offset)
            )
        }
        database.withWriteTransaction {
            database.noteDao().insert(note)
            database.chunkDao().insertAll(entities)
        }
        val stored = database.chunkDao().byNote(noteId)
        KeywordIndex.insertChunks(context, stored)
        _savedNotes.tryEmit(noteId)
        return Summary(noteId, stored.size)
    }

    private fun checkSize(context: Context, uri: Uri) {
        val size = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && index >= 0 && !cursor.isNull(index)) {
                    cursor.getLong(index)
                } else {
                    null
                }
            }
        if (size != null && size > MAX_FILE_BYTES) {
            throw IngestException(IngestException.Kind.TOO_LARGE)
        }
    }

    private fun copyOriginal(context: Context, uri: Uri, noteId: String, format: String): File {
        val directory = File(context.filesDir, "notes")
        directory.mkdirs()
        val target = File(directory, "$noteId.$format")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException()
        input.use { source ->
            target.outputStream().use { sink ->
                source.copyTo(sink)
            }
        }
        return target
    }

    private fun titleOf(text: String, fallback: String): String {
        val firstLine = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return fallback
        val cleaned = firstLine.trimStart('#').trim()
        return cleaned.ifBlank { fallback }.take(40)
    }

    private fun pageOf(pageOffsets: List<Int>, offset: Int): Int? {
        if (pageOffsets.isEmpty()) return null
        val index = pageOffsets.indexOfLast { it <= offset }
        return if (index >= 0) index + 1 else null
    }
}
