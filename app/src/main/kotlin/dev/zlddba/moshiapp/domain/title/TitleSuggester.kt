package dev.zlddba.moshiapp.domain.title

import android.content.Context
import android.util.Log
import dev.zlddba.moshiapp.data.prefs.ModelPrefs
import dev.zlddba.moshiapp.engine.local.GenerationOptions
import dev.zlddba.moshiapp.engine.local.LiteRtLlmEngine
import dev.zlddba.moshiapp.ingest.models.ModelFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TitleSuggester {

    private const val TAG = "TitleSuggester"
    private const val SNIPPET_CHARS = 800
    private const val MAX_TITLE_CHARS = 24

    private const val SYSTEM_PROMPT =
        "你是标题生成助手。根据用户给出的正文，输出一个不超过20字的标题。" +
            "只输出标题本身，不要引号、不要标点结尾、不要解释。"

    private val TRAILING = charArrayOf(
        '。', '，', ',', '.', '：', ':', '；', ';', '！', '!', '？', '?', '、', '"', '\'', '“', '”'
    )

    data class Suggestion(val title: String, val fromModel: Boolean)

    /**
     * 优先用端侧模型总结；模型未就绪或生成失败时退回「取首行」的规则提取。
     */
    suspend fun suggest(context: Context, content: String): Suggestion {
        val fallback = heuristic(content)
        val appContext = context.applicationContext
        val modelId = ModelPrefs(appContext).currentLlm()
        if (!ModelFileManager.isReady(appContext, modelId)) {
            return Suggestion(fallback, fromModel = false)
        }
        val snippet = content.trim().take(SNIPPET_CHARS)
        if (snippet.isEmpty()) return Suggestion(fallback, fromModel = false)
        val raw = try {
            val builder = StringBuilder()
            withContext(Dispatchers.IO) {
                LiteRtLlmEngine.generate(
                    context = appContext,
                    prompt = "正文：\n$snippet\n\n标题：",
                    options = GenerationOptions(
                        systemPrompt = SYSTEM_PROMPT,
                        temperature = 0.2,
                        topK = 20,
                        topP = 0.8,
                        maxOutputTokens = 32
                    ),
                    onToken = { builder.append(it) }
                )
            }
            builder.toString()
        } catch (t: Throwable) {
            Log.w(TAG, "model title failed, fallback to heuristic", t)
            null
        }
        val cleaned = raw?.let { sanitize(it) }.orEmpty()
        return if (cleaned.isNotBlank()) {
            Suggestion(cleaned, fromModel = true)
        } else {
            Suggestion(fallback, fromModel = false)
        }
    }

    fun heuristic(content: String): String {
        val firstLine = content.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        val cleaned = firstLine
            .trimStart('#', '-', '*', '>', ' ', '\t')
            .trim()
        return (cleaned.ifBlank { content.trim() }).take(MAX_TITLE_CHARS)
    }

    fun sanitize(raw: String): String {
        var value = raw.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        listOf("标题：", "标题:", "标题", "《", "》", "\"", "“", "”").forEach { token ->
            value = value.removePrefix(token).removeSuffix(token)
        }
        value = value.trim().trimEnd(*TRAILING).trim()
        return value.take(MAX_TITLE_CHARS)
    }
}
