package dev.zlddba.moshiapp.domain.graph

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphBuilderTest {

    private fun input(id: String) = GraphInput(id = id, title = "标题$id", type = "TEXT")

    @Test
    fun `empty input yields blank graph`() {
        val graph = GraphBuilder.build(emptyList(), emptyList())
        assertTrue(graph.isBlank)
        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun `single note has no edges and sits in the center`() {
        val graph = GraphBuilder.build(listOf(input("a")), listOf(listOf(1f, 0f)))
        assertEquals(1, graph.nodes.size)
        assertTrue(graph.edges.isEmpty())
        assertEquals(0, graph.nodes.first().degree)
        assertEquals(0.5f, graph.nodes.first().x, 0.0001f)
        assertEquals(0.5f, graph.nodes.first().y, 0.0001f)
    }

    @Test
    fun `identical vectors are linked`() {
        val vectors = listOf(listOf(1f, 0f, 0f), listOf(1f, 0f, 0f))
        val graph = GraphBuilder.build(listOf(input("a"), input("b")), vectors)
        assertEquals(1, graph.edges.size)
        assertEquals(1, graph.nodes[0].degree)
        assertEquals(1, graph.nodes[1].degree)
        assertEquals(1f, graph.edges.first().similarity, 0.0001f)
    }

    @Test
    fun `dissimilar vectors stay unlinked`() {
        val vectors = listOf(listOf(1f, 0f), listOf(0f, 1f))
        val graph = GraphBuilder.build(listOf(input("a"), input("b")), vectors)
        assertTrue(graph.edges.isEmpty())
        assertEquals(0, graph.nodes[0].degree)
        assertEquals(0, graph.nodes[1].degree)
    }

    @Test
    fun `node count follows the shorter side`() {
        val graph = GraphBuilder.build(
            listOf(input("a"), input("b"), input("c")),
            listOf(listOf(1f, 0f), listOf(1f, 0f))
        )
        assertEquals(2, graph.nodes.size)
    }

    @Test
    fun `edges per node respect the limit`() {
        val vectors = List(5) { listOf(1f, 0f) }
        val graph = GraphBuilder.build(
            inputs = List(5) { input("n$it") },
            vectors = vectors,
            maxEdgesPerNode = 2
        )
        assertTrue(
            graph.nodes.all { it.degree <= 2 },
            "degrees=${graph.nodes.map { it.degree }}"
        )
        assertTrue(graph.edges.size <= 5, "edges=${graph.edges.size}")
    }

    @Test
    fun `layout positions stay inside margins`() {
        val vectors = listOf(
            listOf(1f, 0f, 0f, 0f),
            listOf(0.9f, 0.1f, 0f, 0f),
            listOf(0f, 1f, 0f, 0f),
            listOf(0f, 0f, 1f, 0f)
        )
        val graph = GraphBuilder.build(List(4) { input("n$it") }, vectors)
        graph.nodes.forEach { node ->
            assertTrue(node.x in 0.08f..0.92f, "x=${node.x}")
            assertTrue(node.y in 0.08f..0.92f, "y=${node.y}")
        }
    }

    @Test
    fun `cosine handles zero and mismatched vectors`() {
        assertEquals(0f, GraphBuilder.cosine(listOf(0f, 0f), listOf(1f, 0f)), 0.0001f)
        assertEquals(1f, GraphBuilder.cosine(listOf(2f, 0f), listOf(5f, 0f)), 0.0001f)
        assertEquals(0f, GraphBuilder.cosine(emptyList(), emptyList()), 0.0001f)
        assertEquals(1f, GraphBuilder.cosine(listOf(1f, 0f), listOf(1f, 0f, 9f)), 0.0001f)
    }
}
