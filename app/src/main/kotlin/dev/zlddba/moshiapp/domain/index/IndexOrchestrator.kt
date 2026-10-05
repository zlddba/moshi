package dev.zlddba.moshiapp.domain.index

import android.content.Context
import android.util.Log
import dev.zlddba.moshiapp.data.db.ChunkEntity
import dev.zlddba.moshiapp.data.db.KeywordIndex
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
                    Log.e(TAG, "sweep failed", e)
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
        val database = MoshiDatabase.get(context)
        repairKeywordIndex(context, database)
        if (!GeckoEmbedding.isReady(context)) {
            Log.w(TAG, "sweep skipped: embedding model not ready")
            return
        }
        val delegate = GeckoEmbedding.delegateTag()
        if (VectorStoreClient.ensureDelegate(context, delegate)) {
            Log.w(TAG, "embedding delegate is now $delegate, rebuilding all vectors")
            database.noteDao().markAllUnindexed()
        }
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

    private suspend fun repairKeywordIndex(context: Context, database: MoshiDatabase) {
        val chunkTotal = database.chunkDao().count()
        if (chunkTotal <= 0) return
        val indexed = KeywordIndex.countAll(context)
        if (indexed < 0) {
            Log.w(TAG, "keyword index unavailable, repair skipped")
            return
        }
        if (indexed >= chunkTotal) return
        Log.w(TAG, "keyword index incomplete indexed=$indexed chunks=$chunkTotal, rebuilding")
        var repaired = 0
        for (note in database.noteDao().recentAll()) {
            val chunks = database.chunkDao().byNote(note.id)
            if (chunks.isEmpty()) continue
            KeywordIndex.insertChunks(context, chunks)
            repaired++
        }
        Log.i(
            TAG,
            "keyword index rebuilt notes=$repaired total=${KeywordIndex.countAll(context)}"
        )
    }

    private suspend fun processBatch(
        context: Context,
        database: MoshiDatabase,
        batch: List<Pair<String, ChunkEntity>>
    ) {
        val vectors = GeckoEmbedding.embedDocuments(context, batch.map { it.second.text })
        if (vectors == null || vectors.size != batch.size) {
            Log.w(TAG, "embed batch failed size=${batch.size} got=${vectors?.size ?: -1}")
            return
        }
        val embedded = batch.mapIndexedNotNull { index, pair ->
            vectors[index]?.let { pair.second to it }
        }
        if (embedded.isEmpty()) {
            Log.w(TAG, "embed produced no vectors size=${batch.size}")
            return
        }
        if (embedded.size < batch.size) {
            Log.w(TAG, "embed partial ok=${embedded.size}/${batch.size}, rest stays pending")
        }
        val chunks = embedded.map { (chunk, _) ->
            VectorStoreClient.VecChunk(chunk.id, chunk.noteId, chunk.text)
        }
        val floats = embedded.map { it.second }
        val delegate = GeckoEmbedding.delegateTag()
        when (VectorStoreClient.insert(context, floats[0].size, delegate, chunks, floats)) {
            VectorStoreClient.InsertOutcome.Ok -> {
                for ((chunk, _) in embedded) {
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
