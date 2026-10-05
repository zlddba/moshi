package dev.zlddba.moshiapp.domain.retrieve

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueryExpansionTest {

    private fun hit(
        chunkId: Int,
        title: String = "笔记$chunkId",
        text: String = "内容$chunkId",
        score: Float = 0.9f
    ) = RetrieveService.Hit(
        chunkId = chunkId,
        noteId = "note$chunkId",
        noteTitle = title,
        text = text,
        pageNo = null,
        score = score
    )

    @Test
    fun `few hits trigger expansion`() {
        assertTrue(QueryExpansion.shouldExpand(emptyList()))
        assertTrue(QueryExpansion.shouldExpand(listOf(hit(1), hit(2))))
    }

    @Test
    fun `low top score triggers expansion`() {
        val hits = listOf(hit(1, score = 0.4f), hit(2, score = 0.3f), hit(3, score = 0.2f))
        assertTrue(QueryExpansion.shouldExpand(hits))
    }

    @Test
    fun `enough strong hits skip expansion`() {
        val hits = listOf(hit(1, score = 0.9f), hit(2, score = 0.8f), hit(3, score = 0.7f))
        assertFalse(QueryExpansion.shouldExpand(hits))
    }

    @Test
    fun `feedback uses best hit title and snippet`() {
        val hits = listOf(
            hit(1, title = "低分笔记", text = "低分内容", score = 0.4f),
            hit(2, title = "加密方式", text = "默识使用 AES-GCM 加密原始文件。", score = 0.8f)
        )
        val queries = QueryExpansion.feedbackQueries("怎么加密", hits)
        assertEquals(2, queries.size)
        assertEquals("加密方式", queries[0])
        assertTrue(queries[1].startsWith("默识使用"))
    }

    @Test
    fun `feedback never repeats the original question and respects the cap`() {
        val hits = listOf(hit(1, title = "怎么加密", text = "怎么加密", score = 0.8f))
        assertTrue(QueryExpansion.feedbackQueries("怎么加密", hits).isEmpty())
        val many = listOf(hit(1, title = "甲", text = "乙", score = 0.9f))
        assertTrue(QueryExpansion.feedbackQueries("问", many).size <= QueryExpansion.MAX_EXTRA_QUERIES)
    }

    @Test
    fun `empty hits produce no feedback queries`() {
        assertTrue(QueryExpansion.feedbackQueries("任意问题", emptyList()).isEmpty())
    }
}
