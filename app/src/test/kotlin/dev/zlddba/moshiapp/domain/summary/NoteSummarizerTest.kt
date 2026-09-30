package dev.zlddba.moshiapp.domain.summary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoteSummarizerTest {

    @Test
    fun `empty content yields empty summary`() {
        assertEquals("", NoteSummarizer.summarize(""))
        assertEquals("", NoteSummarizer.summarize("   \n  "))
    }

    @Test
    fun `short content is returned as is`() {
        val text = "向量检索是把文本转成向量后做相似度搜索。"
        assertEquals(text, NoteSummarizer.summarize(text))
    }

    @Test
    fun `summary stays within char limit`() {
        val text = buildString {
            repeat(40) { append("这是一句用于测试摘要长度限制的中文句子，编号$it。") }
        }
        val summary = NoteSummarizer.summarize(text, limitChars = 120)
        assertTrue(summary.length <= 120, "actual=${summary.length}")
        assertTrue(summary.isNotBlank())
    }

    @Test
    fun `repeated topic sentences win over filler`() {
        val text = "今天天气不错。向量检索依赖向量数据库。向量检索需要向量数据库支持。" +
            "随便说点别的。向量数据库是向量检索的基础。"
        val summary = NoteSummarizer.summarize(text, limitChars = 80)
        assertTrue(summary.contains("向量"), "summary=$summary")
    }

    @Test
    fun `summary does not invent words absent from content`() {
        val text = "默识是一款端侧优先的个人知识库应用。它默认在本地完成解析与检索。"
        val summary = NoteSummarizer.summarize(text)
        val allowed = setOf('。', '，', '、', '；', '：', ' ', '\n')
        summary.forEach { ch ->
            assertTrue(allowed.contains(ch) || text.contains(ch), "invented char: $ch")
        }
    }

    @Test
    fun `markdown headings are stripped from sentences`() {
        val summary = NoteSummarizer.summarize("# 标题一\n## 标题二\n正文内容在这里展开说明。")
        assertFalse(summary.startsWith("#"))
    }

    @Test
    fun `sentences are joined within the limit`() {
        val text = "第一句话足够长可以入选。第二句话也足够长可以入选。第三句话同样足够长。"
        val summary = NoteSummarizer.summarize(text, limitChars = 40)
        assertTrue(summary.length <= 40)
    }
}
