package dev.zlddba.moshiapp.domain.qa

import android.content.Context
import android.util.Log
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.prefs.ModelPrefs
import dev.zlddba.moshiapp.domain.retrieve.RetrieveService
import dev.zlddba.moshiapp.engine.local.LiteRtLlmEngine
import dev.zlddba.moshiapp.ingest.models.ModelFileManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QaOrchestrator {

    const val QA_TOP_K = 5

    private const val TAG = "QaOrchestrator"

    private const val MAX_CONTEXT_CHARS = 3000
    private const val MAX_SNIPPET_CHARS = 600
    private const val SYSTEM_PROMPT =
        "你是「默识」的本地知识助手。请仅依据下列知识库片段回答问题：" +
            "使用中文，回答准确、简洁；" +
            "如果片段中没有足够依据，必须明确说明资料不足，" +
            "不得编造，也不得引入片段之外的知识。"

    sealed interface Outcome {
        data object Refusal : Outcome
        data class ModelMissing(val hits: List<RetrieveService.Hit>) : Outcome
        data class Excerpt(val hits: List<RetrieveService.Hit>) : Outcome
        data class Generated(val hits: List<RetrieveService.Hit>) : Outcome
    }

    suspend fun ask(
        context: Context,
        question: String,
        onToken: (String) -> Unit
    ): Outcome {
        val appContext = context.applicationContext
        return try {
            val hits = RetrieveService.retrieve(appContext, question, QA_TOP_K)
            if (hits.isEmpty()) return Outcome.Refusal
            val modelId = ModelPrefs(appContext).currentLlm()
            if (!ModelFileManager.isReady(appContext, modelId)) {
                return Outcome.ModelMissing(hits)
            }
            val prompt = buildPrompt(question, hits)
            withContext(Dispatchers.IO) {
                try {
                    LiteRtLlmEngine.generate(appContext, prompt, onToken)
                    Outcome.Generated(hits)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.e(TAG, "generate failed hits=${hits.size}", t)
                    Outcome.Excerpt(hits)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "ask failed", t)
            Outcome.Excerpt(emptyList())
        }
    }

    fun excerptText(context: Context, hits: List<RetrieveService.Hit>): String {
        val builder = StringBuilder(context.getString(R.string.chat_excerpt_label))
        for (hit in hits) {
            builder.append("\n\n【")
            builder.append(hit.noteTitle)
            builder.append("】")
            builder.append(hit.text.take(MAX_SNIPPET_CHARS))
        }
        return builder.toString()
    }

    private fun buildPrompt(question: String, hits: List<RetrieveService.Hit>): String {
        val builder = StringBuilder(SYSTEM_PROMPT)
        builder.append("\n\n【知识片段】\n")
        var budget = MAX_CONTEXT_CHARS
        hits.forEachIndexed { index, hit ->
            if (budget <= 0) return@forEachIndexed
            val snippet = hit.text.take(MAX_SNIPPET_CHARS)
            budget -= snippet.length
            builder.append(index + 1)
            builder.append(". 《")
            builder.append(hit.noteTitle)
            builder.append("》")
            val page = hit.pageNo
            if (page != null) {
                builder.append("（第")
                builder.append(page)
                builder.append("页）")
            }
            builder.append('\n')
            builder.append(snippet)
            builder.append('\n')
        }
        builder.append("\n【问题】")
        builder.append(question)
        return builder.toString()
    }
}
