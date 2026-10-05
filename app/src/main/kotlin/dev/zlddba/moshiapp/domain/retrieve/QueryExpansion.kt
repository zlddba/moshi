package dev.zlddba.moshiapp.domain.retrieve

import android.content.Context
import android.util.Log

object QueryExpansion {

    private const val TAG = "QueryExpansion"

    const val MAX_EXTRA_QUERIES = 2
    private const val WEAK_HIT_COUNT = 3
    private const val WEAK_TOP_SCORE = 0.55f
    private const val FEEDBACK_CHARS = 300

    fun shouldExpand(hits: List<RetrieveService.Hit>): Boolean {
        if (hits.size < WEAK_HIT_COUNT) return true
        val top = hits.maxOfOrNull { it.score } ?: 0f
        return top < WEAK_TOP_SCORE
    }

    fun feedbackQueries(
        question: String,
        hits: List<RetrieveService.Hit>
    ): List<String> {
        val best = hits.maxByOrNull { it.score } ?: return emptyList()
        val trimmedQuestion = question.trim()
        val queries = ArrayList<String>(MAX_EXTRA_QUERIES)
        val title = best.noteTitle.trim()
        if (title.isNotEmpty() && title != trimmedQuestion) queries.add(title)
        val snippet = best.text.take(FEEDBACK_CHARS).trim()
        if (snippet.isNotEmpty() && snippet != trimmedQuestion) queries.add(snippet)
        return queries.distinct().take(MAX_EXTRA_QUERIES)
    }

    suspend fun retrieveExpanded(
        context: Context,
        question: String,
        topK: Int
    ): List<RetrieveService.Hit> {
        val base = RetrieveService.retrieve(context, question, topK)
        if (base.isEmpty() || !shouldExpand(base)) {
            Log.i(TAG, "expand skipped hits=${base.size}")
            return base
        }
        val extra = feedbackQueries(question, base)
        if (extra.isEmpty()) return base
        val merged = LinkedHashMap<Int, RetrieveService.Hit>()
        base.forEach { merged[it.chunkId] = it }
        for (query in extra) {
            RetrieveService.retrieve(context, query, topK).forEach { hit ->
                val previous = merged[hit.chunkId]
                if (previous == null || hit.score > previous.score) {
                    merged[hit.chunkId] = hit
                }
            }
        }
        val result = RetrieveService
            .capPerNote(merged.values.sortedByDescending { it.score })
            .take(topK)
        Log.i(TAG, "expand base=${base.size} extra=${extra.size} merged=${result.size}")
        return result
    }
}
