package dev.zlddba.moshiapp.domain.tool

import android.content.Context
import android.util.Log
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.domain.retrieve.RetrieveService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

object KnowledgeTools {

    private const val TAG = "KnowledgeTools"

    const val SEARCH_KNOWLEDGE = "search_knowledge"
    const val GET_NOTE = "get_note"

    const val MAX_RESULT_CHARS = 1200
    const val MAX_ROUNDS = 2
    private const val DEFAULT_TOP_K = 5
    private const val MAX_TOP_K = 8

    data class Spec(
        val name: String,
        val description: String,
        val parametersJson: String
    )

    val SPECS: List<Spec> = listOf(
        Spec(
            name = SEARCH_KNOWLEDGE,
            description = "在用户本地知识库中检索片段。已有片段不足以回答、或需要补充证据时调用。" +
                "返回带编号的片段，含所属笔记标题与正文。",
            parametersJson = "{\"type\":\"object\",\"properties\":{" +
                "\"query\":{\"type\":\"string\",\"description\":\"检索用的关键词或问句\"}," +
                "\"top_k\":{\"type\":\"integer\",\"description\":\"返回片段数，1 到 8，默认 5\"}}," +
                "\"required\":[\"query\"]}"
        ),
        Spec(
            name = GET_NOTE,
            description = "按笔记编号读取该笔记的正文，用于查看检索片段所在的完整上下文。" +
                "编号来自检索结果中的 note_id。",
            parametersJson = "{\"type\":\"object\",\"properties\":{" +
                "\"note_id\":{\"type\":\"string\",\"description\":\"笔记编号\"}}," +
                "\"required\":[\"note_id\"]}"
        )
    )

    data class Outcome(
        val text: String,
        val hits: List<RetrieveService.Hit>,
        val ok: Boolean
    )

    suspend fun execute(
        context: Context,
        name: String,
        argumentsJson: String,
        allowSensitive: Boolean
    ): Outcome {
        val appContext = context.applicationContext
        val args = parseArguments(argumentsJson)
        Log.i(TAG, "call name=$name keys=${args.keys} allowSensitive=$allowSensitive")
        return when (name) {
            SEARCH_KNOWLEDGE -> search(appContext, args, allowSensitive)
            GET_NOTE -> readNote(appContext, args, allowSensitive)
            else -> Outcome(
                text = "未知工具 $name，可用工具：$SEARCH_KNOWLEDGE、$GET_NOTE",
                hits = emptyList(),
                ok = false
            )
        }
    }

    private suspend fun search(
        context: Context,
        args: JsonObject,
        allowSensitive: Boolean
    ): Outcome {
        val query = args.stringArg("query")
        if (query.isNullOrBlank()) return Outcome("缺少 query 参数", emptyList(), false)
        val topK = (args.intArg("top_k") ?: DEFAULT_TOP_K).coerceIn(1, MAX_TOP_K)
        val found = RetrieveService.capPerNote(
            RetrieveService.retrieve(context, query, topK)
        )
        if (found.isEmpty()) {
            return Outcome("知识库中没有检索到与「$query」相关的片段", emptyList(), true)
        }
        val kept = if (allowSensitive) found else found.filter { !it.isSensitive }
        if (kept.isEmpty()) {
            return Outcome("检索到的片段均被标记为敏感，已按隐私设置拦截", emptyList(), true)
        }
        val omitted = found.size - kept.size
        val builder = StringBuilder("检索「")
        builder.append(query)
        builder.append("」命中 ")
        builder.append(kept.size)
        builder.append(" 个片段：")
        if (omitted > 0) {
            builder.append("（另有 ")
            builder.append(omitted)
            builder.append(" 个敏感片段已拦截）")
        }
        kept.forEachIndexed { index, hit ->
            builder.append("\n\n")
            builder.append(index + 1)
            builder.append(". 《")
            builder.append(hit.noteTitle)
            builder.append("》 note_id=")
            builder.append(hit.noteId)
            val page = hit.pageNo
            if (page != null) {
                builder.append(" 第")
                builder.append(page)
                builder.append("页")
            }
            builder.append('\n')
            builder.append(hit.text)
        }
        return Outcome(truncate(builder.toString()), kept, true)
    }

    private suspend fun readNote(
        context: Context,
        args: JsonObject,
        allowSensitive: Boolean
    ): Outcome {
        val noteId = args.stringArg("note_id")
        if (noteId.isNullOrBlank()) return Outcome("缺少 note_id 参数", emptyList(), false)
        val note = MoshiDatabase.get(context).noteDao().byId(noteId)
            ?: return Outcome("找不到编号为 $noteId 的笔记", emptyList(), false)
        if (note.isSensitive && !allowSensitive) {
            return Outcome("该笔记被标记为敏感，禁止外发", emptyList(), false)
        }
        val builder = StringBuilder("《")
        builder.append(note.title)
        builder.append("》\n")
        builder.append(note.content)
        return Outcome(truncate(builder.toString()), emptyList(), true)
    }

    private fun parseArguments(json: String): JsonObject {
        val raw = json.trim().ifBlank { "{}" }
        return try {
            Json.parseToJsonElement(raw).jsonObject
        } catch (e: Throwable) {
            Log.w(TAG, "argument parse failed: $raw", e)
            JsonObject(emptyMap())
        }
    }

    private fun JsonObject.stringArg(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun JsonObject.intArg(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun truncate(text: String): String =
        if (text.length <= MAX_RESULT_CHARS) text else text.take(MAX_RESULT_CHARS) + "\n（内容已截断）"
}
