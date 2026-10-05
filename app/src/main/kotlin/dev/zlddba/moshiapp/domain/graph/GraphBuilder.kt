package dev.zlddba.moshiapp.domain.graph

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class GraphInput(
    val id: String,
    val title: String,
    val type: String
)

data class GraphNode(
    val id: String,
    val title: String,
    val type: String,
    val degree: Int,
    val x: Float,
    val y: Float
)

data class GraphEdge(
    val fromIndex: Int,
    val toIndex: Int,
    val similarity: Float
)

data class KnowledgeGraph(
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList()
) {
    val isBlank: Boolean get() = nodes.isEmpty()
}

object GraphBuilder {

    const val DEFAULT_MIN_SIMILARITY = 0.45f
    const val DEFAULT_MAX_EDGES_PER_NODE = 3

    private const val LAYOUT_ITERATIONS = 320
    private const val LAYOUT_MARGIN = 0.08f
    private const val INITIAL_TEMPERATURE = 0.12f
    private const val MIN_TEMPERATURE = 0.001f
    private const val EPSILON = 1e-4f
    private const val PAIR_STRIDE = 100000L

    fun build(
        inputs: List<GraphInput>,
        vectors: List<List<Float>>,
        minSimilarity: Float = DEFAULT_MIN_SIMILARITY,
        maxEdgesPerNode: Int = DEFAULT_MAX_EDGES_PER_NODE
    ): KnowledgeGraph {
        val size = minOf(inputs.size, vectors.size)
        if (size == 0) return KnowledgeGraph()
        val keptInputs = inputs.take(size)
        val keptVectors = vectors.take(size)
        val edges = linkedEdges(keptVectors, minSimilarity, maxEdgesPerNode)
        val degrees = IntArray(size)
        for (edge in edges) {
            degrees[edge.fromIndex]++
            degrees[edge.toIndex]++
        }
        val positions = layout(size, edges)
        val nodes = keptInputs.mapIndexed { index, input ->
            val position = positions[index]
            GraphNode(
                id = input.id,
                title = input.title,
                type = input.type,
                degree = degrees[index],
                x = position.first,
                y = position.second
            )
        }
        return KnowledgeGraph(nodes = nodes, edges = edges)
    }

    fun cosine(a: List<Float>, b: List<Float>): Float {
        val size = minOf(a.size, b.size)
        if (size == 0) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (index in 0 until size) {
            val left = a[index]
            val right = b[index]
            dot += left * right
            normA += left * left
            normB += right * right
        }
        if (normA <= EPSILON || normB <= EPSILON) return 0f
        val denominator = sqrt(normA) * sqrt(normB)
        if (denominator <= EPSILON) return 0f
        return (dot / denominator).coerceIn(-1f, 1f)
    }

    private fun linkedEdges(
        vectors: List<List<Float>>,
        minSimilarity: Float,
        maxEdgesPerNode: Int
    ): List<GraphEdge> {
        val size = vectors.size
        if (size < 2) return emptyList()
        val limit = maxEdgesPerNode.coerceAtLeast(1)
        val perNode = ArrayList<List<Pair<Int, Float>>>(size)
        for (i in 0 until size) {
            val scored = ArrayList<Pair<Int, Float>>(size - 1)
            for (j in 0 until size) {
                if (i == j) continue
                val similarity = cosine(vectors[i], vectors[j])
                if (similarity >= minSimilarity) scored.add(j to similarity)
            }
            scored.sortByDescending { it.second }
            perNode.add(scored.take(limit))
        }
        val seen = HashSet<Long>()
        val edges = ArrayList<GraphEdge>()
        for (i in 0 until size) {
            for ((j, similarity) in perNode[i]) {
                if (!seen.add(pairKey(i, j))) continue
                edges.add(GraphEdge(fromIndex = i, toIndex = j, similarity = similarity))
            }
        }
        return edges
    }

    private fun pairKey(a: Int, b: Int): Long =
        minOf(a, b).toLong() * PAIR_STRIDE + maxOf(a, b).toLong()

    private fun layout(size: Int, edges: List<GraphEdge>): List<Pair<Float, Float>> {
        if (size == 1) return listOf(0.5f to 0.5f)
        val positionX = FloatArray(size)
        val positionY = FloatArray(size)
        for (index in 0 until size) {
            val angle = (2.0 * PI * index / size).toFloat()
            positionX[index] = 0.5f + 0.35f * cos(angle)
            positionY[index] = 0.5f + 0.35f * sin(angle)
        }
        val idealDistance = sqrt(1f / size)
        val displacementX = FloatArray(size)
        val displacementY = FloatArray(size)
        var temperature = INITIAL_TEMPERATURE
        val cooling = (INITIAL_TEMPERATURE - MIN_TEMPERATURE) / (LAYOUT_ITERATIONS + 1)
        repeat(LAYOUT_ITERATIONS) {
            displacementX.fill(0f)
            displacementY.fill(0f)
            for (i in 0 until size) {
                for (j in i + 1 until size) {
                    var dx = positionX[i] - positionX[j]
                    var dy = positionY[i] - positionY[j]
                    var distance = sqrt(dx * dx + dy * dy)
                    if (distance < EPSILON) {
                        dx = EPSILON
                        dy = EPSILON
                        distance = EPSILON
                    }
                    val force = (idealDistance * idealDistance) / distance
                    val unitX = dx / distance
                    val unitY = dy / distance
                    displacementX[i] += unitX * force
                    displacementY[i] += unitY * force
                    displacementX[j] -= unitX * force
                    displacementY[j] -= unitY * force
                }
            }
            for (edge in edges) {
                var dx = positionX[edge.fromIndex] - positionX[edge.toIndex]
                var dy = positionY[edge.fromIndex] - positionY[edge.toIndex]
                var distance = sqrt(dx * dx + dy * dy)
                if (distance < EPSILON) {
                    dx = EPSILON
                    dy = EPSILON
                    distance = EPSILON
                }
                val force = (distance * distance) / idealDistance
                val unitX = dx / distance
                val unitY = dy / distance
                displacementX[edge.fromIndex] -= unitX * force
                displacementY[edge.fromIndex] -= unitY * force
                displacementX[edge.toIndex] += unitX * force
                displacementY[edge.toIndex] += unitY * force
            }
            for (index in 0 until size) {
                val length = sqrt(
                    displacementX[index] * displacementX[index] +
                        displacementY[index] * displacementY[index]
                )
                if (length <= EPSILON) continue
                val step = minOf(length, temperature)
                positionX[index] += displacementX[index] / length * step
                positionY[index] += displacementY[index] / length * step
            }
            temperature = max(temperature - cooling, MIN_TEMPERATURE)
        }
        return normalize(positionX, positionY)
    }

    private fun normalize(x: FloatArray, y: FloatArray): List<Pair<Float, Float>> {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (index in x.indices) {
            minX = minOf(minX, x[index])
            maxX = maxOf(maxX, x[index])
            minY = minOf(minY, y[index])
            maxY = maxOf(maxY, y[index])
        }
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val span = maxOf(maxX - minX, maxY - minY, EPSILON)
        return x.indices.map { index ->
            val normalizedX = 0.5f + (x[index] - centerX) / span
            val normalizedY = 0.5f + (y[index] - centerY) / span
            normalizedX.coerceIn(LAYOUT_MARGIN, 1f - LAYOUT_MARGIN) to
                normalizedY.coerceIn(LAYOUT_MARGIN, 1f - LAYOUT_MARGIN)
        }
    }
}
