package dev.zlddba.moshiapp.domain.index

import android.content.Context
import android.util.Log
import dev.zlddba.moshiapp.data.db.ChunkEntity
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.data.repo.IngestRepository
import dev.zlddba.moshiapp.data.vector.VectorStoreClient
import dev.zlddba.moshiapp.engine.embedding.GeckoEmbedding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

object IndexOrchestrator {

    private const val BATCH_SIZE = 16
    private const val TAG = "IndexOrchestrator"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trigger = Channel<Unit>(Channel.CONFLATED)

    private var started = false

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        scope.launch {
            IngestRepository.savedNotes.collect { trigger.trySend(Unit) }
        }
        scope.launch {
            for (unit in trigger) {
                try {
                    sweep(appContext)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                }
            }
        }
        trigger.trySend(Unit)
    }

    fun requestSweep() {
        if (!started) return
        trigger.trySend(Unit)
    }

    private suspend fun sweep(context: Context) {
        if (!GeckoEmbedding.isReady(context)) return
        val database = MoshiDatabase.get(context)
        val notes = database.noteDao().notIndexed()
        Log.i(TAG, "sweep pending notes=${notes.size}")
        if (notes.isEmpty()) return
        val work = LinkedHashMap<String, MutableList<ChunkEntity>>()
        for (note in notes) {
            database.noteDao().updateStatus(note.id, NoteEntity.STATUS_INDEXING)
            val unembedded = database.chunkDao().byNote(note.id)
                .filter { it.embeddingId == null }
            if (unembedded.isNotEmpty()) {
                work[note.id] = unembedded.toMutableList()
            }
        }
        if (work.isNotEmpty()) {
            val items = ArrayList<Pair<String, ChunkEntity>>()
            for ((noteId, chunks) in work) {
                for (chunk in chunks) {
                    items.add(noteId to chunk)
                }
            }
            var index = 0
            while (index < items.size) {
                val end = minOf(index + BATCH_SIZE, items.size)
                processBatch(context, database, items.subList(index, end))
                index = end
            }
        }
        for (note in notes) {
            val unembedded = database.chunkDao().unembeddedCount(note.id)
            val status = if (unembedded == 0) {
                NoteEntity.STATUS_INDEXED
            } else {
                NoteEntity.STATUS_FAILED
            }
            Log.i(TAG, "note ${note.id} unembedded=$unembedded -> $status")
            database.noteDao().updateStatus(note.id, status)
        }
    }

    private suspend fun processBatch(
        context: Context,
        database: MoshiDatabase,
        batch: List<Pair<String, ChunkEntity>>
    ) {
        val vectors = GeckoEmbedding.embedDocuments(context, batch.map { it.second.text })
        if (vectors == null || vectors.size != batch.size || vectors.isEmpty()) {
            Log.w(TAG, "embed batch failed size=${batch.size} got=${vectors?.size ?: -1}")
            return
        }
        val chunks = batch.map { (noteId, chunk) ->
            VectorStoreClient.VecChunk(chunk.id, noteId, chunk.text)
        }
        when (VectorStoreClient.insert(context, vectors[0].size, chunks, vectors)) {
            VectorStoreClient.InsertOutcome.Ok -> {
                for ((_, chunk) in batch) {
                    database.chunkDao().markEmbedded(chunk.id)
                }
            }
            VectorStoreClient.InsertOutcome.DimReset -> {
                database.chunkDao().clearEmbeddings()
                trigger.trySend(Unit)
            }
            VectorStoreClient.InsertOutcome.Unavailable -> {
                Log.w(TAG, "vector insert unavailable size=${batch.size}")
            }
        }
    }
}
