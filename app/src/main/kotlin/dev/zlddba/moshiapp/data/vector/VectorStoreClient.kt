package dev.zlddba.moshiapp.data.vector

import android.content.Context
import android.util.Log
import com.google.ai.edge.localagents.rag.memory.ColumnConfig
import com.google.ai.edge.localagents.rag.memory.SqliteVectorStore
import com.google.ai.edge.localagents.rag.memory.TableConfig
import com.google.ai.edge.localagents.rag.memory.VectorStoreRecord
import com.google.common.collect.ImmutableList
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object VectorStoreClient {

    const val MIN_SCORE = -1f

    data class VecChunk(
        val chunkId: Int,
        val noteId: String,
        val text: String
    )

    data class VecHit(
        val chunkId: Int,
        val noteId: String,
        val similarity: Float
    )

    sealed interface InsertOutcome {
        data object Ok : InsertOutcome
        data object Unavailable : InsertOutcome
        data object DimReset : InsertOutcome
    }

    private sealed interface OpenResult {
        data class Ready(val store: SqliteVectorStore) : OpenResult
        data object DimReset : OpenResult
        data object Unavailable : OpenResult
    }

    private const val TABLE_NAME = "moshi_vectors"
    private const val TEXT_COLUMN = "text"
    private const val EMBEDDINGS_COLUMN = "embeddings"
    private const val DATABASE_NAME = "moshi_vec.db"
    private const val TAG = "VectorStoreClient"

    private val mutex = Mutex()

    @Volatile
    private var store: SqliteVectorStore? = null

    @Volatile
    private var storeDim = 0

    @Volatile
    private var unavailable = false

    suspend fun insert(
        context: Context,
        dim: Int,
        chunks: List<VecChunk>,
        vectors: List<List<Float>>
    ): InsertOutcome {
        if (chunks.isEmpty()) return InsertOutcome.Ok
        if (dim <= 0 || vectors.size != chunks.size) return InsertOutcome.Unavailable
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val appContext = context.applicationContext
                var opened = openLocked(appContext, dim)
                var reset = false
                if (opened is OpenResult.DimReset) {
                    opened = openLocked(appContext, dim)
                    reset = true
                }
                val ready = opened as? OpenResult.Ready
                    ?: return@withLock InsertOutcome.Unavailable
                for (index in chunks.indices) {
                    val chunk = chunks[index]
                    val record = VectorStoreRecord.create(
                        chunk.text,
                        ImmutableList.copyOf(vectors[index])
                    )
                        .toBuilder()
                        .addMetadata("chunk_id", chunk.chunkId)
                        .addMetadata("note_id", chunk.noteId)
                        .build()
                    try {
                        ready.store.insert(record)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Log.e(TAG, "insert failed chunk=${chunk.chunkId} dim=$dim", e)
                        return@withLock InsertOutcome.Unavailable
                    }
                }
                Log.i(TAG, "insert ok size=${chunks.size} dim=$dim")
                writeDimMarker(appContext, dim)
                if (reset) InsertOutcome.DimReset else InsertOutcome.Ok
            }
        }
    }

    suspend fun deleteByNote(context: Context, noteId: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val appContext = context.applicationContext
                if (store == null) {
                    val dim = readDimMarker(appContext) ?: return@withLock
                    if (dim <= 0) return@withLock
                    val opened = openLocked(appContext, dim)
                    if (opened !is OpenResult.Ready) return@withLock
                }
                try {
                    store?.sqlQuery("DELETE FROM $TABLE_NAME WHERE note_id = '$noteId'")
                    Log.i(TAG, "deleteByNote note=$noteId")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e(TAG, "deleteByNote failed note=$noteId", e)
                }
            }
        }
    }

    suspend fun clearAll(context: Context) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val appContext = context.applicationContext
                val existing = store
                if (existing != null) {
                    try {
                        existing.sqlQuery("DELETE FROM $TABLE_NAME")
                        Log.i(TAG, "clearAll rows removed")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Log.e(TAG, "clearAll failed", e)
                    }
                } else {
                    deleteDatabaseFile(appContext)
                    Log.i(TAG, "clearAll store files removed")
                }
            }
        }
    }

    private fun writeDimMarker(context: Context, dim: Int) {
        try {
            val directory = File(context.filesDir, "vectors")
            directory.mkdirs()
            File(directory, "dim.txt").writeText(dim.toString())
        } catch (e: Throwable) {
            Log.w(TAG, "writeDimMarker failed dim=$dim", e)
        }
    }

    private fun readDimMarker(context: Context): Int? = try {
        File(File(context.filesDir, "vectors"), "dim.txt").readText().trim().toIntOrNull()
    } catch (e: Throwable) {
        null
    }

    suspend fun search(context: Context, query: List<Float>, k: Int): List<VecHit> {
        if (query.isEmpty() || k <= 0) return emptyList()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val opened = openLocked(context.applicationContext, query.size)
                val ready = opened as? OpenResult.Ready
                if (ready == null) {
                    Log.w(TAG, "search store not ready dim=${query.size}")
                    return@withLock emptyList<VecHit>()
                }
                try {
                    val records = ready.store.getNearestRecords(query, k, MIN_SCORE)
                    if (records.isEmpty()) {
                        Log.w(TAG, "search raw=0 k=$k dim=${query.size}")
                    } else {
                        Log.i(
                            TAG,
                            "search raw=${records.size} sampleMeta=${records.first().metadata}"
                        )
                    }
                    val found = records.mapNotNull { record ->
                        val metadata = record.metadata
                        val chunkId = metadataMeta(metadata["chunk_id"])
                            ?: return@mapNotNull null
                        val noteId = metadata["note_id"] as? String
                            ?: return@mapNotNull null
                        val similarity = metadataSimilarity(metadata["temp_similarity_score"])
                        VecHit(chunkId, noteId, similarity)
                    }
                    Log.i(TAG, "search hits=${found.size} k=$k dim=${query.size}")
                    found
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e(TAG, "search failed dim=${query.size} k=$k", e)
                    emptyList()
                }
            }
        }
    }

    private fun metadataMeta(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    private fun metadataSimilarity(value: Any?): Float = when (value) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull() ?: 0f
        else -> 0f
    }

    private fun openLocked(context: Context, dim: Int): OpenResult {
        if (unavailable) return OpenResult.Unavailable
        store?.let { existing ->
            if (storeDim == dim) return OpenResult.Ready(existing)
            store = null
            storeDim = 0
            deleteDatabaseFile(context)
            return OpenResult.DimReset
        }
        if (dim <= 0) return OpenResult.Unavailable
        val directory = File(context.filesDir, "vectors")
        directory.mkdirs()
        val file = File(directory, DATABASE_NAME)
        return try {
            val created = SqliteVectorStore(
                dim,
                file.absolutePath,
                TEXT_COLUMN,
                EMBEDDINGS_COLUMN,
                tableConfig()
            )
            store = created
            storeDim = dim
            Log.i(TAG, "store opened dim=$dim")
            OpenResult.Ready(created)
        } catch (first: Throwable) {
            Log.e(TAG, "store open failed dim=$dim, deleting and retry", first)
            file.delete()
            try {
                val created = SqliteVectorStore(
                    dim,
                    file.absolutePath,
                    TEXT_COLUMN,
                    EMBEDDINGS_COLUMN,
                    tableConfig()
                )
                store = created
                storeDim = dim
                OpenResult.Ready(created)
            } catch (second: Throwable) {
                Log.e(TAG, "store open failed permanently dim=$dim", second)
                unavailable = true
                OpenResult.Unavailable
            }
        }
    }

    private fun deleteDatabaseFile(context: Context) {
        val directory = File(context.filesDir, "vectors")
        directory.listFiles()?.forEach { file ->
            if (file.name == DATABASE_NAME || file.name.startsWith("$DATABASE_NAME-")) {
                file.delete()
            }
        }
    }

    private fun tableConfig(): TableConfig = TableConfig.builder()
        .setName(TABLE_NAME)
        .addColumn(
            ColumnConfig.builder()
                .setName("ROWID")
                .setSqlType("INTEGER")
                .setKeyType(ColumnConfig.KeyType.PRIMARY_KEY)
                .setAutoIncrement(true)
                .setIsNullable(false)
                .build()
        )
        .addColumn(ColumnConfig.create(TEXT_COLUMN, "TEXT"))
        .addColumn(ColumnConfig.create("embeddings", "REAL"))
        .addColumn(ColumnConfig.create("chunk_id", "INTEGER"))
        .addColumn(ColumnConfig.create("note_id", "TEXT"))
        .build()
}
