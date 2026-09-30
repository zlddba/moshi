package dev.zlddba.moshiapp.domain.qa

import dev.zlddba.moshiapp.ingest.models.ModelCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QaFormatTest {

    @Test
    fun `small model maps to small profile`() {
        assertEquals(QaFormat.Profile.SMALL, QaFormat.of(ModelCatalog.QWEN))
    }

    @Test
    fun `gemma maps to standard profile`() {
        assertEquals(QaFormat.Profile.STANDARD, QaFormat.of(ModelCatalog.GEMMA))
    }

    @Test
    fun `unknown model falls back to standard profile`() {
        assertEquals(QaFormat.Profile.STANDARD, QaFormat.of("not_a_model"))
    }

    @Test
    fun `only qwen is treated as small model`() {
        assertTrue(ModelCatalog.isSmallModel(ModelCatalog.QWEN))
        assertFalse(ModelCatalog.isSmallModel(ModelCatalog.GEMMA))
    }

    @Test
    fun `small profile constrains output and turns down randomness`() {
        val small = QaFormat.options(QaFormat.Profile.SMALL)
        val standard = QaFormat.options(QaFormat.Profile.STANDARD)
        assertNotNull(small.constraint)
        assertTrue(small.temperature < standard.temperature)
        assertTrue(small.topK < standard.topK)
        assertTrue(small.maxOutputTokens < standard.maxOutputTokens)
        assertNotNull(small.thinkingTokenBudget)
    }

    @Test
    fun `standard profile keeps free generation`() {
        val standard = QaFormat.options(QaFormat.Profile.STANDARD)
        assertNull(standard.constraint)
        assertNull(standard.thinkingTokenBudget)
    }

    @Test
    fun `constraint regex matches the required skeleton`() {
        val pattern = Regex(QaFormat.options(QaFormat.Profile.SMALL).constraint!!)
        assertTrue(pattern.matches("[THINKING] 片段1给出定义。[ANSWER] 默识是端侧知识库。"))
        assertTrue(pattern.matches("[THINKING] 分析\n[ANSWER] 结论\n依据：片段2"))
        assertFalse(pattern.matches("[ANSWER] 缺少思考段"))
        assertFalse(pattern.matches("[THINKING] 只有思考没有答案"))
    }

    @Test
    fun `both prompts carry the fixed refusal copy`() {
        for (profile in QaFormat.Profile.entries) {
            val prompt = QaFormat.systemPrompt(profile)
            assertTrue(prompt.contains(QaFormat.REFUSAL), "profile=$profile")
            assertTrue(prompt.contains(QaFormat.MARK_THINK), "profile=$profile")
            assertTrue(prompt.contains(QaFormat.MARK_ANSWER), "profile=$profile")
        }
    }

    @Test
    fun `small prompt stays short and shows an example`() {
        val small = QaFormat.systemPrompt(QaFormat.Profile.SMALL)
        val standard = QaFormat.systemPrompt(QaFormat.Profile.STANDARD)
        assertTrue(small.length < standard.length)
        assertTrue(small.contains("示例"))
    }

    @Test
    fun `marker detection helpers`() {
        assertTrue(QaFormat.hasThinkingMarker("[THINKING] x"))
        assertFalse(QaFormat.hasThinkingMarker("[ANSWER] x"))
        assertTrue(QaFormat.hasAnswerMarker("【答案】x"))
        assertFalse(QaFormat.hasAnswerMarker("随便写点东西"))
    }
}
