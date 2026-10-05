package dev.zlddba.moshiapp.domain.qa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnswerParseTest {

    private fun parse(raw: String) = QaFormat.parse(raw)

    @Test
    fun `parses thinking and answer sections`() {
        val parsed = parse("[THINKING] 先归纳片段\n[ANSWER] 结论是X")
        assertEquals("先归纳片段", parsed.thinking)
        assertEquals("结论是X", parsed.answer)
        assertFalse(parsed.refused)
    }

    @Test
    fun `strips leading colon after markers`() {
        val parsed = parse("[THINKING]: 分析\n[ANSWER]: 答案")
        assertEquals("分析", parsed.thinking)
        assertEquals("答案", parsed.answer)
    }

    @Test
    fun `answer only marker keeps empty thinking`() {
        val parsed = parse("[ANSWER] 直接结论")
        assertEquals("", parsed.thinking)
        assertEquals("直接结论", parsed.answer)
    }

    @Test
    fun `thinking without answer marker falls back to thinking`() {
        val parsed = parse("[THINKING] 还在分析")
        assertEquals("还在分析", parsed.thinking)
        assertEquals("还在分析", parsed.answer)
    }

    @Test
    fun `text without markers is taken as answer`() {
        val parsed = parse("模型没按格式输出")
        assertEquals("", parsed.thinking)
        assertEquals("模型没按格式输出", parsed.answer)
    }

    @Test
    fun `markers inside answer body do not confuse split`() {
        val parsed = parse("[THINKING] 提到 [ANSWER] 这个词\n[ANSWER] 正文")
        assertEquals("提到 [ANSWER] 这个词", parsed.thinking)
        assertEquals("正文", parsed.answer)
    }

    @Test
    fun `chinese markers are accepted`() {
        val parsed = parse("【思考】先做归纳\n【答案】结论如下")
        assertEquals("先做归纳", parsed.thinking)
        assertEquals("结论如下", parsed.answer)
    }

    @Test
    fun `chinese section headings without markers are accepted`() {
        val parsed = parse("思考过程：片段1给出了定义。\n答案：默识是端侧知识库。")
        assertEquals("片段1给出了定义。", parsed.thinking)
        assertEquals("默识是端侧知识库。", parsed.answer)
    }

    @Test
    fun `answer heading alone is accepted`() {
        val parsed = parse("答案：只有答案没有思考")
        assertEquals("", parsed.thinking)
        assertEquals("只有答案没有思考", parsed.answer)
    }

    @Test
    fun `trailing source label is stripped`() {
        val parsed = parse("[THINKING] 分析\n[ANSWER] 结论正文\n参考来源：1")
        assertEquals("结论正文", parsed.answer)
    }

    @Test
    fun `body words like source and basis are preserved`() {
        assertEquals("依据片段1的说明得出结论", parse("依据片段1的说明得出结论").answer)
        assertEquals("该说法来源可靠", parse("[ANSWER] 该说法来源可靠").answer)
    }

    @Test
    fun `dangling citation bracket is stripped`() {
        val parsed = parse("[THINKING] 分析\n[ANSWER] 结论正文 [1")
        assertEquals("结论正文", parsed.answer)
    }

    @Test
    fun `fixed refusal is detected`() {
        val parsed = parse("[THINKING] 片段无关\n[ANSWER] 知识库中没有找到相关内容")
        assertTrue(parsed.refused)
        assertEquals(QaFormat.REFUSAL, parsed.answer)
    }

    @Test
    fun `polite refusal is detected as refusal`() {
        assertTrue(parse("[ANSWER] 抱歉，我无法回答这个问题。").refused)
        assertTrue(parse("[ANSWER] 依据不足，无法作答。").refused)
        assertTrue(parse("资料不足").refused)
    }

    @Test
    fun `normal answer is not a refusal`() {
        assertFalse(parse("[ANSWER] 向量检索是把文本转成向量后做相似度搜索。").refused)
        assertFalse(parse("[ANSWER] 默识支持本地与云端两种引擎。").refused)
    }

    @Test
    fun `answer mentioning refusal wording is not a refusal`() {
        assertFalse(
            parse("[ANSWER] 如果检索不到，系统会输出知识库中没有找到相关内容").refused
        )
        assertFalse(
            parse("[ANSWER] 依据不足时模块逐字输出知识库中没有找到相关内容").refused
        )
        assertFalse(parse("[ANSWER] 资料不足会导致检索分数低于阈值").refused)
        assertFalse(parse("[ANSWER] 命中关键词并不代表模型无法回答，需要结构判定").refused)
        assertFalse(parse("[ANSWER] 当片段与问题无关时模型会无法回答，转而拒答").refused)
        assertFalse(parse("[ANSWER] 知识片段没有相关内容。").refused)
        assertFalse(parse("[ANSWER] 本条没有相关记录。").refused)
        assertFalse(parse("[ANSWER] 例如资料不足，系统就拒答").refused)
    }

    @Test
    fun `near-miss refusal wording is still detected`() {
        assertTrue(parse("[ANSWER] 知识库中没有找到相关的内容。").refused)
        assertTrue(parse("[ANSWER] 知识库中没有相关内容。").refused)
        assertTrue(parse("[ANSWER] 知识库中未找到相关内容。").refused)
        assertTrue(parse("[ANSWER] 没有找到相关的内容").refused)
        assertTrue(parse("[ANSWER] 未检索到相关信息。").refused)
        assertTrue(parse("[ANSWER] 知识库中没有对应内容").refused)
    }

    @Test
    fun `refusal wording keeps answer text intact`() {
        val parsed = parse("[ANSWER] 如果检索不到，系统会输出知识库中没有找到相关内容")
        assertEquals("如果检索不到，系统会输出知识库中没有找到相关内容", parsed.answer)
    }

    @Test
    fun `refusal at the head of the answer is detected`() {
        assertTrue(parse("[ANSWER] 资料不足。").refused)
        assertTrue(parse("[ANSWER] 没有相关内容。").refused)
        assertTrue(parse("[ANSWER] 抱歉，不能提供该信息").refused)
        assertTrue(parse("[ANSWER] 知识库中没有找到相关内容，无法确认").refused)
    }

    @Test
    fun `thinking only without fallback leaves answer blank`() {
        val parsed = QaFormat.parse("[THINKING] 还在分析", fallbackToThinking = false)
        assertEquals("还在分析", parsed.thinking)
        assertEquals("", parsed.answer)
    }

    @Test
    fun `empty input yields empty result`() {
        val parsed = parse("   ")
        assertEquals("", parsed.thinking)
        assertEquals("", parsed.answer)
        assertFalse(parsed.refused)
    }

    @Test
    fun `plain fallback extracts answer section`() {
        assertEquals(
            "这是答案",
            QaFormat.plainFallback("随便说一句\n【答案】这是答案")
        )
        assertEquals("", QaFormat.plainFallback("没有任何分段标题"))
    }
}
