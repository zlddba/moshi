package dev.zlddba.moshiapp.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room3.withWriteTransaction
import dev.zlddba.moshiapp.data.db.ChunkEntity
import dev.zlddba.moshiapp.data.db.KeywordIndex
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.data.db.NoteTagCrossRef
import dev.zlddba.moshiapp.data.db.TagEntity
import dev.zlddba.moshiapp.data.vector.VectorStoreClient
import dev.zlddba.moshiapp.domain.chunk.Chunker
import dev.zlddba.moshiapp.ingest.parse.IngestException
import dev.zlddba.moshiapp.ingest.parse.ParsedDoc
import dev.zlddba.moshiapp.ingest.parse.ParserRegistry
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object IngestRepository {

    const val MAX_FILE_BYTES = 20L * 1024 * 1024

    private val _savedNotes = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val savedNotes: SharedFlow<String> = _savedNotes.asSharedFlow()

    private val _deletedNotes = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val deletedNotes: SharedFlow<String> = _deletedNotes.asSharedFlow()

    private val _libraryCleared = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val libraryCleared: SharedFlow<Unit> = _libraryCleared.asSharedFlow()

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

    data class TagInfo(
        val id: Int,
        val name: String,
        val refCount: Int
    )

    private const val MAX_TAG_CHARS = 16
    private const val MAX_TAGS_PER_NOTE = 10

    fun normalizeTags(raw: String): List<String> {
        val seen = LinkedHashSet<String>()
        raw.split(',', '，', ';', '；', '#', '/')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { tag ->
                val clipped = tag.take(MAX_TAG_CHARS)
                if (seen.size < MAX_TAGS_PER_NOTE) seen.add(clipped)
            }
        return seen.toList()
    }

    suspend fun listTags(context: Context): List<TagInfo> = withContext(Dispatchers.IO) {
        MoshiDatabase.get(context).tagsDao().allWithCount().map {
            TagInfo(id = it.id, name = it.name, refCount = it.refCount)
        }
    }

    suspend fun tagsOf(context: Context, noteId: String): List<String> = withContext(Dispatchers.IO) {
        MoshiDatabase.get(context).tagsDao().tagsOfNote(noteId).map { it.name }
    }

    suspend fun setTags(context: Context, noteId: String, raw: String): List<String> =
        withContext(Dispatchers.IO) {
            val names = normalizeTags(raw)
            val database = MoshiDatabase.get(context)
            val dao = database.tagsDao()
            database.withWriteTransaction {
                dao.clearNote(noteId)
                val now = System.currentTimeMillis()
                for (name in names) {
                    dao.insertTag(TagEntity(name = name, createdAt = now))
                    val id = dao.byName(name)?.id ?: continue
                    dao.link(NoteTagCrossRef(noteId = noteId, tagId = id))
                }
                dao.deleteOrphans()
            }
            names
        }

    suspend fun renameTag(context: Context, tagId: Int, newName: String): Boolean =        withContext(Dispatchers.IO) {
            val name = normalizeTags(newName).firstOrNull() ?: return@withContext false
            val dao = MoshiDatabase.get(context).tagsDao()
            val current = dao.byId(tagId) ?: return@withContext false
            if (current.name == name) return@withContext true
            val existing = dao.byName(name)
            if (existing != null) {
                for (noteId in MoshiDatabase.get(context).noteDao().byTag(tagId).map { it.id }) {
                    dao.link(NoteTagCrossRef(noteId = noteId, tagId = existing.id))
                }
                dao.deleteTag(tagId)
            } else {
                dao.rename(tagId, name)
            }
            dao.deleteOrphans()
            true
        }

    suspend fun deleteTag(context: Context, tagId: Int) = withContext(Dispatchers.IO) {
        val dao = MoshiDatabase.get(context).tagsDao()
        dao.clearTag(tagId)
        dao.deleteTag(tagId)
    }

    suspend fun updateNoteTitle(context: Context, noteId: String, title: String) =
        withContext(Dispatchers.IO) {
            val value = title.trim().take(80)
            if (value.isEmpty()) return@withContext
            MoshiDatabase.get(context).noteDao()
                .updateTitle(noteId, value, System.currentTimeMillis())
        }

    suspend fun updateNoteSummary(context: Context, noteId: String, summary: String) =
        withContext(Dispatchers.IO) {
            MoshiDatabase.get(context).noteDao()
                .updateSummary(noteId, summary.trim().take(600), System.currentTimeMillis())
        }

    /**
     * 编辑正文：重建分块、刷新关键词索引、清掉旧向量并重新排队索引。
     */
    suspend fun updateNoteContent(
        context: Context,
        noteId: String,
        content: String,
        retainEditedAt: Boolean = true
    ): Int = withContext(Dispatchers.IO) {
        val text = content.trim()
        if (text.isEmpty()) throw IngestException(IngestException.Kind.EMPTY)
        val chunks = Chunker.chunk(text)
        if (chunks.isEmpty()) throw IngestException(IngestException.Kind.EMPTY)
        val database = MoshiDatabase.get(context)
        val note = database.noteDao().byId(noteId)
            ?: throw IngestException(IngestException.Kind.IO)
        val keepPage = note.type == NoteEntity.TYPE_PDF
        val oldPages = database.chunkDao().byNote(noteId).map { it.pageNo }
        val entities = chunks.mapIndexed { index, chunk ->
            ChunkEntity(
                noteId = noteId,
                seq = chunk.seq,
                text = chunk.text,
                charOffset = chunk.offset,
                pageNo = if (keepPage) oldPages.getOrNull(index) else null
            )
        }
        val now = System.currentTimeMillis()
        database.withWriteTransaction {
            database.chunkDao().deleteByNote(noteId)
            database.chunkDao().insertAll(entities)
            database.noteDao().insert(
                note.copy(
                    content = text,
                    indexStatus = NoteEntity.STATUS_PENDING,
                    updatedAt = if (retainEditedAt) now else note.updatedAt
                )
            )
        }
        VectorStoreClient.deleteByNote(context, noteId)
        KeywordIndex.deleteNote(context, noteId)
        KeywordIndex.insertChunks(context, database.chunkDao().byNote(noteId))
        _savedNotes.tryEmit(noteId)
        entities.size
    }
    suspend fun importFile(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String?,
        title: String = "",
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
            title = title.trim().ifEmpty { parsed.title },
            content = parsed.text,
            type = noteTypeOf(parsed.format),
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
        title: String = "",
        sourceNote: String? = null,
        sourceUri: Uri? = null,
        isBuiltIn: Boolean = false
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
            title = title.trim().ifEmpty { titleOf(content, fallbackTitle) },
            content = content,
            type = type,
            pageOffsets = emptyList(),
            chunks = chunks,
            sourceUri = storedSource,
            sourceNote = sourceNote,
            isBuiltIn = isBuiltIn
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

    suspend fun clearAll(context: Context) {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val database = MoshiDatabase.get(appContext)
            database.withWriteTransaction {
                database.chunkDao().deleteAll()
                database.noteDao().deleteAll()
                database.qaLogDao().deleteAll()
                database.tagsDao().clearAllLinks()
                database.tagsDao().deleteAll()
            }
            KeywordIndex.clearAll(appContext)
            VectorStoreClient.clearAll(appContext)
            deleteStoredSources(appContext)
            _libraryCleared.tryEmit(Unit)
            HelpSeeder.start(appContext)
        }
    }

    private fun deleteStoredSources(context: Context) {
        try {
            val notesDir = File(context.filesDir, "notes")
            notesDir.listFiles()?.forEach { file ->
                if (file.isFile) file.delete()
            }
        } catch (e: Throwable) {
        }
    }

    suspend fun exportJson(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val database = MoshiDatabase.get(appContext)
        val notes = database.noteDao().recentAll().filterNot { it.isBuiltIn }
        val root = JSONObject()
        root.put("app", "moshi")
        root.put("format", 1)
        root.put("exported_at", System.currentTimeMillis())
        val noteArray = JSONArray()
        for (note in notes) {
            val noteObj = JSONObject()
            noteObj.put("id", note.id)
            noteObj.put("type", note.type)
            noteObj.put("title", note.title)
            noteObj.put("content", note.content)
            noteObj.put("created_at", note.createdAt)
            note.sourceNote?.let { noteObj.put("source_note", it) }
            val chunkArray = JSONArray()
            for (chunk in database.chunkDao().byNote(note.id)) {
                val chunkObj = JSONObject()
                chunkObj.put("seq", chunk.seq)
                chunkObj.put("text", chunk.text)
                chunk.pageNo?.let { chunkObj.put("page_no", it) }
                chunkArray.put(chunkObj)
            }
            noteObj.put("chunks", chunkArray)
            noteArray.put(noteObj)
        }
        root.put("notes", noteArray)
        val output = appContext.contentResolver.openOutputStream(uri, "wt")
            ?: appContext.contentResolver.openOutputStream(uri)
            ?: throw IOException()
        output.use { sink ->
            sink.write(root.toString(2).toByteArray(Charsets.UTF_8))
        }
        notes.size
    }

    fun exportSuggestionName(): String =
        "moshi-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"

    private fun noteTypeOf(format: String): String = when (format) {
        ParsedDoc.FORMAT_PDF -> NoteEntity.TYPE_PDF
        ParsedDoc.FORMAT_XLSX, ParsedDoc.FORMAT_XLS -> NoteEntity.TYPE_SHEET
        else -> NoteEntity.TYPE_TEXT
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
        sourceNote: String? = null,
        isBuiltIn: Boolean = false
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
            isBuiltIn = isBuiltIn,
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
