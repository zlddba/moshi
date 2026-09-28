package dev.zlddba.moshiapp.domain.retrieve

import android.content.Context
import android.util.Log
import dev.zlddba.moshiapp.data.db.KeywordIndex
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.data.vector.VectorStoreClient
import dev.zlddba.moshiapp.engine.embedding.GeckoEmbedding

object RetrieveService {

    const val TOP_K = 5
    const val VEC_RECALL_K = 20
    const val KW_RECALL_K = 20
    const val RELEVANCE_THRESHOLD = 0.35f

    private const val TAG = "RetrieveService"
    private const val RRF_K = 60

    data class Hit(
        val chunkId: Int,
        val noteId: String,
        val noteTitle: String,
        val text: String,
        val pageNo: Int?,
        val score: Float
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
        Log.i(TAG, "fused=${fused.size} scores=${fused.map { it.second }}")
        if (fused.isEmpty()) return emptyList()
        val chunkDao = MoshiDatabase.get(appContext).chunkDao()
        val noteDao = MoshiDatabase.get(appContext).noteDao()
        val titleCache = HashMap<String, String>()
        val hits = ArrayList<Hit>(fused.size)
        for ((chunkId, score) in fused) {
            val chunk = chunkDao.byId(chunkId) ?: continue
            val title = titleCache.getOrPut(chunk.noteId) {
                noteDao.byId(chunk.noteId)?.title.orEmpty()
            }
            hits.add(
                Hit(
                    chunkId = chunk.id,
                    noteId = chunk.noteId,
                    noteTitle = title,
                    text = chunk.text,
                    pageNo = chunk.pageNo,
                    score = score
                )
            )
        }
        return hits
    }

    internal suspend fun recallVector(context: Context, query: String): List<Int> {
        val vector = GeckoEmbedding.embedQuery(context, query) ?: return emptyList()
        return VectorStoreClient.search(context, vector, VEC_RECALL_K)
            .map { it.chunkId }
            .distinct()
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
        val maxRaw = 2f / (RRF_K + 1)
        return scores.entries
            .map { entry -> entry.key to (entry.value / maxRaw) }
            .filter { it.second >= RELEVANCE_THRESHOLD }
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
