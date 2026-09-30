package dev.zlddba.moshiapp.domain.qa

import dev.zlddba.moshiapp.domain.retrieve.RetrieveService
import dev.zlddba.moshiapp.ingest.models.ModelCatalog

object QaFormat {

    const val MARK_THINK = "[THINKING]"
    const val MARK_ANSWER = "[ANSWER]"

    const val REFUSAL = "知识库中没有找到相关内容"

    private const val MARK_THINK_CN = "【思考】"
    private const val MARK_ANSWER_CN = "【答案】"

    private val THINK_MARKERS = listOf(MARK_THINK, MARK_THINK_CN)
    private val ANSWER_MARKERS = listOf(MARK_ANSWER, MARK_ANSWER_CN)

    private val THINK_PREFIXES = listOf("思考过程", "思考", "分析过程", "分析", "理由", "推理")
    private val ANSWER_PREFIXES = listOf("最终结论", "结论", "答案", "回答", "答复", "答")

    private val TRAILING_NOISE = Regex(
        "\\s*[（(【\\[]?\\s*(参考来源|引用来源|来源|依据|出处|参考文献)\\s*[》」】\\]]?\\s*[：:]\\s*" +
            "([^\\n]{0,40}|\\d{1,2}|[（(【\\[]\\s*\\d{1,2}\\s*[）)】\\]])\\s*[》」】\\]]?\\s*$|" +
            "\\s*[（(【\\[]\\s*\\d{1,2}\\s*[）)】\\]]\\s*$"
    )

    private val DANGLING_INDEX = Regex("\\s*[\\[【（(]\\s*\\d+\\s*$")

    private val THINK_BLOCK = Regex(
        "\\s*[（(【\\[]?\\s*(?:思考过程|思考|分析过程|分析|推理)\\s*[）)】\\]]?\\s*[：:]\\s*(.*)$",
        RegexOption.DOT_MATCHES_ALL
    )

    private val ANSWER_BLOCK = Regex(
        "\\s*[（(【\\[]?\\s*(?:最终结论|结论|答案|回答|答复|答)\\s*[）)】\\]]?\\s*[：:]\\s*(.*)$",
        RegexOption.DOT_MATCHES_ALL
    )

    private val STRICT_REFUSALS = listOf(
        "没有找到相关内容",
        "未找到相关内容",
        "没有相关内容",
        "资料不足",
        "依据不足",
        "信息不足",
        "无法回答",
        "无法作答",
        "不能回答"
    )

    private val NEGATION_PATTERN =
        Regex("^(?:抱歉[，,]?)?(?:我)?(?:暂时)?(?:无法|不能|不便)(?:回答|作答|提供|告知|给出)")

    private val NEAR_REFUSAL_PATTERN =
        Regex(
            "^.{0,4}?(?:没有|未|无法)(?:找到|看到|检索到|发现|提供)?" +
                "(?:相关|对应|有效|可用)[\\s\\S]{0,3}"
        )

    private const val REFUSAL_LEAD_SLACK = 4
    private const val REFUSAL_TAIL_SLACK = 3

    private val SCOPE_WORDS = listOf("片段", "本条", "该条", "此条", "该段", "此段")

    enum class Profile { SMALL, STANDARD }

    data class Options(
        val systemPrompt: String,
        val temperature: Double,
        val topK: Int,
        val topP: Double,
        val maxOutputTokens: Int,
        val constraint: String?,
        val thinkingTokenBudget: Int?
    )

    data class ParsedAnswer(
        val thinking: String,
        val answer: String,
        val refused: Boolean
    )

    fun of(modelId: String): Profile =
        if (ModelCatalog.isSmallModel(modelId)) Profile.SMALL else Profile.STANDARD

    private const val REFUSAL_RULE =
        "只使用【知识片段】中的信息作答，不得引入片段之外的知识，" +
            "不得编造；片段与问题无关或依据不足时，最后一行的答案必须逐字输出：$REFUSAL"

    private const val SMALL_SYSTEM_PROMPT =
        "你是知识库问答助手。严格按下面两行格式输出，不要输出任何其他内容：\n" +
            "$MARK_THINK 一句话说明依据\n" +
            "$MARK_ANSWER 答案正文\n\n" +
            "示例：\n" +
            "$MARK_THINK 片段1给出了定义。\n" +
            "$MARK_ANSWER 默识是一款端侧优先的个人知识库应用。\n\n" +
            "规则：\n" +
            "1. 必须写满两行，$MARK_ANSWER 那一行不能省略，也不能提前结束。\n" +
            "2. 语言与问题一致；不要 markdown、编号或额外解释。\n" +
            "3. $REFUSAL_RULE"

    private const val STANDARD_SYSTEM_PROMPT =
        "你是「默识」的知识助手。输出必须严格遵循以下两部分的固定格式，不得输出其他内容：" +
            "$MARK_THINK 通读下方知识片段，归纳、比较、推断，写出分析过程；" +
            "$MARK_ANSWER 给出最终结论，先说结论、再给依据，中文、准确、简洁。" +
            "要求：结论必须能由片段支撑，可换用自己的表述，不要整段照搬；" +
            "不得引入片段之外的事实，不得编造；" +
            "如果片段与问题无关或依据不足，$MARK_ANSWER 必须逐字固定输出：" +
            REFUSAL +
            "，不得使用任何其他措辞，不得输出这句话以外的内容。"

    private const val SMALL_CONSTRAINT =
        "\\s*\\[THINKING\\][\\s\\S]{1,400}?\\[ANSWER\\][\\s\\S]{1,1600}"

    fun systemPrompt(profile: Profile): String = when (profile) {
        Profile.SMALL -> SMALL_SYSTEM_PROMPT
        Profile.STANDARD -> STANDARD_SYSTEM_PROMPT
    }

    fun options(profile: Profile): Options = when (profile) {
        Profile.SMALL -> Options(
            systemPrompt = SMALL_SYSTEM_PROMPT,
            temperature = 0.1,
            topK = 20,
            topP = 0.8,
            maxOutputTokens = 512,
            constraint = SMALL_CONSTRAINT,
            thinkingTokenBudget = 96
        )

        Profile.STANDARD -> Options(
            systemPrompt = STANDARD_SYSTEM_PROMPT,
            temperature = 0.3,
            topK = 40,
            topP = 0.95,
            maxOutputTokens = 1024,
            constraint = null,
            thinkingTokenBudget = null
        )
    }

    const val MAX_CONTEXT_CHARS = 3000
    const val MAX_SNIPPET_CHARS = 600

    fun userPrompt(question: String, hits: List<RetrieveService.Hit>): String {
        val builder = StringBuilder("【知识片段】\n")
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

    fun parse(raw: String, fallbackToThinking: Boolean = true): ParsedAnswer {
        var text = raw.trim()
        if (text.isEmpty()) return ParsedAnswer("", "", false)

        var thinking = ""
        val thinkIndex = indexOfMarker(text, THINK_MARKERS)
        if (thinkIndex >= 0) {
            val bodyStart = thinkIndex + markerLengthAt(text, thinkIndex, THINK_MARKERS)
            val answerIndex = indexOfMarker(text, ANSWER_MARKERS)
            val body = if (answerIndex > bodyStart) {
                text.substring(bodyStart, answerIndex)
            } else {
                text.substring(bodyStart)
            }
            thinking = clean(body, THINK_PREFIXES)
            text = if (answerIndex > bodyStart) text.substring(answerIndex) else ""
        }

        var answer = text.trim()
        if (answer.isEmpty()) return ParsedAnswer(thinking, "", refused = false)

        val cnMarker = indexOfMarker(answer, ANSWER_MARKERS)
        if (cnMarker >= 0) {
            answer = answer.substring(cnMarker + markerLengthAt(answer, cnMarker, ANSWER_MARKERS))
        } else {
            answer = stripLeading(answer, ANSWER_PREFIXES)
        }
        answer = clean(answer, emptyList())
        if (answer.isEmpty() && thinking.isNotEmpty()) {
            val fallback = if (fallbackToThinking) thinking else ""
            return ParsedAnswer(thinking, fallback, refused = isRefusal(fallback))
        }
        return ParsedAnswer(thinking, answer, refused = isRefusal(answer))
    }

    private fun indexOfMarker(text: String, markers: List<String>): Int {
        var found = -1
        for (marker in markers) {
            val at = text.indexOf(marker)
            if (at >= 0 && (found < 0 || at < found)) found = at
        }
        return found
    }

    private fun markerLengthAt(text: String, index: Int, markers: List<String>): Int =
        markers.firstOrNull { text.startsWith(it, index) }?.length ?: 0

    private fun clean(body: String, prefixes: List<String>): String {
        var value = stripLeading(body.trim(), prefixes)
        while (true) {
            val trimmed = value.trimEnd()
            val next = trimmed
                .replace(TRAILING_NOISE, "")
                .replace(DANGLING_INDEX, "")
            if (next == trimmed) break
            value = next
        }
        return value.trim()
    }

    private fun stripLeading(text: String, prefixes: List<String>): String {
        var value = text.trimStart().trimStart('：', ':').trimStart()
        var changed = true
        while (changed) {
            changed = false
            for (prefix in prefixes) {
                if (!value.startsWith(prefix)) continue
                val rest = value.substring(prefix.length)
                val marker = rest.trimStart()
                if (marker.startsWith("：") || marker.startsWith(":")) {
                    value = marker.substring(1).trimStart()
                    changed = true
                    break
                }
            }
        }
        return value
    }

    fun plainFallback(raw: String): String {
        val match = ANSWER_BLOCK.find(raw) ?: return ""
        return clean(match.groupValues[1], ANSWER_PREFIXES)
    }

    fun isRefusal(text: String): Boolean {
        val compact = text.replace(" ", "").replace("\n", "").trim()
        if (compact.isEmpty()) return false
        if (startsWithPhrase(compact, REFUSAL)) return true
        if (STRICT_REFUSALS.any { startsWithPhrase(compact, it) }) return true
        if (NEGATION_PATTERN.containsMatchIn(compact)) return true
        return isNearRefusal(compact)
    }

    private fun startsWithPhrase(text: String, phrase: String): Boolean {
        if (!text.startsWith(phrase)) return false
        val rest = text.substring(phrase.length)
        return rest.isEmpty() || rest.first() in "。！？；，、.!?;,"
    }

    /**
     * 模型常把固定文案写成「知识库中没有找到相关的内容」这类近义说法，
     * 只要该句式出现在答案开头且后面就是句末，就仍按拒答处理。
     */
    private fun isNearRefusal(text: String): Boolean {
        val match = NEAR_REFUSAL_PATTERN.find(text) ?: return false
        if (match.range.first > REFUSAL_LEAD_SLACK) return false
        if (SCOPE_WORDS.any { text.substring(0, match.range.last + 1).contains(it) }) return false
        val end = match.range.last + 1
        if (end >= text.length) return true
        val tail = text.substring(end)
        return tail.length <= REFUSAL_TAIL_SLACK && tail.all { it in "。！？；，、.!?;," }
    }

    fun hasThinkingMarker(raw: String): Boolean = indexOfMarker(raw, THINK_MARKERS) >= 0

    fun hasAnswerMarker(raw: String): Boolean = indexOfMarker(raw, ANSWER_MARKERS) >= 0
}
