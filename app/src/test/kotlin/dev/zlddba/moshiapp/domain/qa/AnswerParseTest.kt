package dev.zlddba.moshiapp.domain.qa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnswerParseTest {

    @Test
    fun `parses thinking and answer sections`() {
        val parsed = QaOrchestrator.parseAnswer("[THINKING] 先归纳片段\n[ANSWER] 结论是X")
        assertEquals("先归纳片段", parsed.thinking)
        assertEquals("结论是X", parsed.answer)
    }

    @Test
    fun `strips leading colon after markers`() {
        val parsed = QaOrchestrator.parseAnswer("[THINKING]: 分析\n[ANSWER]: 答案")
        assertEquals("分析", parsed.thinking)
        assertEquals("答案", parsed.answer)
    }

    @Test
    fun `answer only marker keeps empty thinking`() {
        val parsed = QaOrchestrator.parseAnswer("[ANSWER] 直接结论")
        assertEquals("", parsed.thinking)
        assertEquals("直接结论", parsed.answer)
    }

    @Test
    fun `thinking without answer marker leaves answer blank`() {
        val parsed = QaOrchestrator.parseAnswer("[THINKING] 还在分析")
        assertEquals("还在分析", parsed.thinking)
        assertEquals("", parsed.answer)
    }

    @Test
    fun `text without markers is taken as answer`() {
        val parsed = QaOrchestrator.parseAnswer("模型没按格式输出")
        assertEquals("", parsed.thinking)
        assertEquals("模型没按格式输出", parsed.answer)
    }

    @Test
    fun `markers inside answer body do not confuse split`() {
        val parsed = QaOrchestrator.parseAnswer(
            "[THINKING] 提到 [ANSWER] 这个词\n[ANSWER] 正文"
        )
        assertEquals("提到 [ANSWER] 这个词", parsed.thinking)
        assertEquals("正文", parsed.answer)
    }
}
