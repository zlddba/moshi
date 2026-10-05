package dev.zlddba.moshiapp.domain.retrieve

import android.content.Context
import android.util.Log
import dev.zlddba.moshiapp.data.db.KeywordIndex
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.data.vector.VectorStoreClient
import dev.zlddba.moshiapp.domain.index.IndexOrchestrator
import dev.zlddba.moshiapp.engine.embedding.GeckoEmbedding

object RetrieveService {

    const val TOP_K = 5
    const val VEC_RECALL_K = 20
    const val KW_RECALL_K = 20
    const val RELEVANCE_THRESHOLD = 0.35f
    const val VEC_SIM_MIN = 0.5f
    const val RELATIVE_RATIO = 0.8f
    const val RELATED_TOP_K = 3
    const val RELATED_RECALL_K = 24

    private const val TAG = "RetrieveService"
    private const val RRF_K = 60

    data class Hit(
        val chunkId: Int,
        val noteId: String,
        val noteTitle: String,
        val text: String,
        val pageNo: Int?,
        val score: Float,
        val isSensitive: Boolean = false,
        val isBuiltIn: Boolean = false
    )

    data class RelatedNote(
        val noteId: String,
        val title: String,
        val similarity: Float
    )

    suspend fun retrieve(context: Context, question: String, topK: Int = TOP_K): List<Hit> {
        val query = question.trim()
        if (query.isEmpty() || topK <= 0) return emptyList()
        val appContext = context.applicationContext
        val vecIds = recallVector(appContext, query)
        val kwIds = recallKeyword(appContext, query)
        Log.i(TAG, "recall vec=${vecIds.size} kw=${kwIds.size} query=$query")
        if (vecIds.isEmpty() && kwIds.isEmpty()) return emptyList()
        val fused = fuse(vecIds, kwIds, topK)
        val topScore = fused.maxOfOrNull { it.second } ?: 0f
        Log.i(
            TAG,
            "fused=${fused.size} scores=${fused.map { it.second }} " +
                "top=$topScore gate=${maxOf(RELEVANCE_THRESHOLD, RELATIVE_RATIO * topScore)}"
        )
        if (fused.isEmpty()) return emptyList()
        val chunkDao = MoshiDatabase.get(appContext).chunkDao()
        val noteDao = MoshiDatabase.get(appContext).noteDao()
        val noteCache = HashMap<String, NoteEntity>()
        val hits = ArrayList<Hit>(fused.size)
        for ((chunkId, score) in fused) {
            val chunk = chunkDao.byId(chunkId) ?: continue
            val note = noteCache[chunk.noteId]
                ?: noteDao.byId(chunk.noteId)?.also { noteCache[chunk.noteId] = it }
            hits.add(
                Hit(
                    chunkId = chunk.id,
                    noteId = chunk.noteId,
                    noteTitle = note?.title.orEmpty(),
                    text = chunk.text,
                    pageNo = chunk.pageNo,
                    score = score,
                    isSensitive = note?.isSensitive == true,
                    isBuiltIn = note?.isBuiltIn == true
                )
            )
        }
        return hits
    }

    suspend fun relatedNotes(
        context: Context,
        noteId: String,
        queryText: String,
        topK: Int = RELATED_TOP_K
    ): List<RelatedNote> {
        val query = queryText.trim()
        if (query.isEmpty() || noteId.isEmpty() || topK <= 0) return emptyList()
        val appContext = context.applicationContext
        val vector = GeckoEmbedding.embedQuery(appContext, query) ?: return emptyList()
        reconcileDelegate(appContext)
        val hits = VectorStoreClient.search(
            appContext,
            vector,
            RELATED_RECALL_K,
            GeckoEmbedding.delegateTag()
        )
        if (hits.isEmpty()) return emptyList()
        val best = HashMap<String, Float>()
        for (hit in hits) {
            if (hit.noteId == noteId) continue
            val previous = best[hit.noteId]
            if (previous == null || hit.similarity > previous) {
                best[hit.noteId] = hit.similarity
            }
        }
        if (best.isEmpty()) return emptyList()
        val noteDao = MoshiDatabase.get(appContext).noteDao()
        val related = best.mapNotNull { (otherId, similarity) ->
            val note = noteDao.byId(otherId) ?: return@mapNotNull null
            if (note.isBuiltIn) return@mapNotNull null
            RelatedNote(noteId = note.id, title = note.title, similarity = similarity)
        }
            .sortedByDescending { it.similarity }
            .take(topK)
        Log.i(TAG, "relatedNotes note=$noteId hits=${hits.size} kept=${related.size}")
        return related
    }

    private suspend fun reconcileDelegate(context: Context) {
        val delegate = GeckoEmbedding.delegateTag()
        if (!VectorStoreClient.ensureDelegate(context, delegate)) return
        Log.w(TAG, "embedding delegate is now $delegate, scheduling vector rebuild")
        MoshiDatabase.get(context).noteDao().markAllUnindexed()
        IndexOrchestrator.requestSweep()
    }

    internal suspend fun recallVector(context: Context, query: String): List<Int> {
        val vector = GeckoEmbedding.embedQuery(context, query) ?: return emptyList()
        reconcileDelegate(context)
        val hits = VectorStoreClient.search(
            context,
            vector,
            VEC_RECALL_K,
            GeckoEmbedding.delegateTag()
        )
        val kept = hits.filter { it.similarity >= VEC_SIM_MIN }
        Log.i(
            TAG,
            "vec gate kept=${kept.size}/${hits.size} simMin=$VEC_SIM_MIN " +
                "sims=${hits.map { it.similarity }}"
        )
        return kept.map { it.chunkId }.distinct()
    }

    internal suspend fun recallKeyword(context: Context, query: String): List<Int> {
        val hits = KeywordIndex.search(context, query, KW_RECALL_K)
        return hits.map { it.chunkId }.distinct()
    }

    internal fun fuse(vecIds: List<Int>, kwIds: List<Int>, topK: Int): List<Pair<Int, Float>> {
        if (vecIds.isEmpty() && kwIds.isEmpty()) return emptyList()
        val scores = HashMap<Int, Float>()
        accumulate(scores, vecIds)
        accumulate(scores, kwIds)
        if (scores.isEmpty()) return emptyList()
        val maxRaw = 2f / (RRF_K + 1)
        val normalized = scores.entries
            .map { entry -> entry.key to (entry.value / maxRaw) }
        val top = normalized.maxOf { it.second }
        val gate = maxOf(RELEVANCE_THRESHOLD, RELATIVE_RATIO * top)
        return normalized
            .filter { it.second >= gate }
            .sortedByDescending { it.second }
            .take(topK)
    }

    private fun accumulate(scores: HashMap<Int, Float>, ids: List<Int>) {
        val seen = HashSet<Int>()
        var rank = 1
        for (id in ids) {
            if (!seen.add(id)) continue
            scores[id] = (scores[id] ?: 0f) + 1f / (RRF_K + rank)
            rank++
        }
    }
}
