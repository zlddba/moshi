package dev.zlddba.moshiapp.domain.retrieve

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RetrieveServiceTest {

    @Test
    fun `chunk in both lists ranks above single list chunk`() {
        val fused = RetrieveService.fuse(listOf(1), listOf(1, 2), 5)
        assertEquals(2, fused.size)
        assertEquals(1, fused[0].first)
        assertEquals(2, fused[1].first)
        assertEquals(1.0f, fused[0].second, 0.0001f)
        assertEquals(61f / 124f, fused[1].second, 0.0001f)
    }

    @Test
    fun `single list first rank normalizes to half`() {
        val fused = RetrieveService.fuse(listOf(7), emptyList(), 5)
        assertEquals(1, fused.size)
        assertEquals(0.5f, fused[0].second, 0.0001f)
    }

    @Test
    fun `duplicate ids inside one list count once`() {
        val fused = RetrieveService.fuse(listOf(1, 1, 2), emptyList(), 5)
        assertEquals(2, fused.size)
        assertEquals(1, fused[0].first)
        assertEquals(0.5f, fused[0].second, 0.0001f)
        assertEquals(61f / 124f, fused[1].second, 0.0001f)
    }

    @Test
    fun `topK limits and orders by score descending`() {
        val fused = RetrieveService.fuse(listOf(3, 1, 2), emptyList(), 2)
        assertEquals(2, fused.size)
        assertEquals(3, fused[0].first)
        assertEquals(1, fused[1].first)
        assertTrue(fused[0].second > fused[1].second)
    }

    @Test
    fun `empty recalls fuse to empty`() {
        val fused = RetrieveService.fuse(emptyList(), emptyList(), 5)
        assertTrue(fused.isEmpty())
    }

    @Test
    fun `all twenty ranked hits clear the relevance threshold`() {
        val fused = RetrieveService.fuse(emptyList(), (1..20).toList(), 5)
        assertEquals(5, fused.size)
        for ((_, score) in fused) {
            assertTrue(score >= RetrieveService.RELEVANCE_THRESHOLD)
        }
    }
}
