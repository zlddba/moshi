package dev.zlddba.moshiapp.activities.graphPage

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.activities.graphPage.GraphViewModel.GraphEvent
import dev.zlddba.moshiapp.activities.graphPage.GraphViewModel.GraphStage
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.domain.graph.GraphEdge
import dev.zlddba.moshiapp.domain.graph.GraphNode
import dev.zlddba.moshiapp.domain.graph.KnowledgeGraph
import dev.zlddba.moshiapp.ui.theme.MoshiShapeLarge
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import kotlin.math.hypot

private const val MIN_SCALE = 0.6f
private const val MAX_SCALE = 3.5f
private const val GRAPH_PADDING_RATIO = 0.12f
private const val LABEL_CHARS = 8
private const val LABEL_ALL_LIMIT = 12
private const val HUB_DEGREE = 2

@Composable
fun GraphPageScreen(
    uiState: GraphViewModel.GraphUiState,
    onBack: () -> Unit,
    onNodeClick: (String) -> Unit,
    onEvent: (GraphEvent) -> Unit,
    onOpenModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        PageTopBar(
            titleRes = R.string.graph_title,
            backDescRes = R.string.graph_back,
            onBack = onBack
        )
        when (uiState.stage) {
            GraphStage.Loading -> GraphLoadingState()

            GraphStage.Empty -> GraphMessageState(
                icon = Icons.Outlined.Shield,
                titleRes = R.string.graph_empty_title,
                bodyRes = R.string.graph_empty_body
            )

            GraphStage.EmbeddingMissing -> GraphMessageState(
                icon = Icons.Outlined.Hub,
                titleRes = R.string.graph_embed_title,
                bodyRes = R.string.graph_embed_body,
                actionTextRes = R.string.graph_embed_action,
                onAction = onOpenModel
            )

            GraphStage.Failed -> GraphMessageState(
                icon = Icons.Outlined.AccountTree,
                titleRes = R.string.graph_failed_title,
                bodyRes = R.string.graph_failed_body,
                actionTextRes = R.string.graph_retry,
                onAction = { onEvent(GraphEvent.Reload) }
            )

            GraphStage.Ready -> GraphReadyContent(
                uiState = uiState,
                onNodeClick = onNodeClick,
                onEvent = onEvent
            )
        }
    }
}

@Composable
private fun ColumnScope.GraphReadyContent(
    uiState: GraphViewModel.GraphUiState,
    onNodeClick: (String) -> Unit,
    onEvent: (GraphEvent) -> Unit
) {
    GraphStatBar(
        uiState = uiState,
        onReload = { onEvent(GraphEvent.Reload) }
    )
    GraphCanvas(
        graph = uiState.graph,
        onNodeClick = onNodeClick,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
    )
    GraphHintBar()
}

@Composable
private fun GraphStatBar(
    uiState: GraphViewModel.GraphUiState,
    onReload: () -> Unit
) {
    val scannedText = stringResource(R.string.graph_scanned_fmt, uiState.scanned)
    val ignoredText = if (uiState.ignored > 0) {
        stringResource(R.string.graph_ignored_fmt, uiState.analyzed)
    } else {
        null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.graph_stat_fmt,
                    uiState.graph.nodes.size,
                    uiState.graph.edges.size
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (ignoredText == null) scannedText else scannedText + " · " + ignoredText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        TextButton(onClick = onReload) {
            Text(stringResource(R.string.graph_reload))
        }
    }
}

@Composable
private fun GraphHintBar() {
    Text(
        text = stringResource(R.string.graph_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun GraphCanvas(
    graph: KnowledgeGraph,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        offset += panChange
    }

    val density = LocalDensity.current
    val edgeColor = MaterialTheme.colorScheme.primary
    val nodeColor = MaterialTheme.colorScheme.primary
    val hubColor = MaterialTheme.colorScheme.tertiary
    val ringColor = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurface

    val baseRadiusPx = with(density) { 9.dp.toPx() }
    val radiusStepPx = with(density) { 2.2.dp.toPx() }
    val maxRadiusPx = with(density) { 22.dp.toPx() }
    val labelGapPx = with(density) { 5.dp.toPx() }
    val labelSizePx = with(density) { 10.sp.toPx() }
    val touchSlopPx = with(density) { 12.dp.toPx() }
    val densityScale = density.density

    val labelPaint = remember(labelColor, labelSizePx) {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = labelColor.toArgb()
            textSize = labelSizePx
        }
    }

    val showAllLabels = graph.nodes.size <= LABEL_ALL_LIMIT

    Canvas(
        modifier = modifier
            .clipToBounds()
            .transformable(state = transformState)
            .pointerInput(graph) {
                detectTapGestures { position ->
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    var hitId: String? = null
                    var hitDistance = Float.MAX_VALUE
                    graph.nodes.forEach { node ->
                        val center = toScreen(node.x, node.y, width, height, scale, offset)
                        val reach = radiusFor(
                            node.degree,
                            baseRadiusPx,
                            radiusStepPx,
                            maxRadiusPx
                        ) + touchSlopPx
                        val distance = hypot(center.x - position.x, center.y - position.y)
                        if (distance <= reach && distance < hitDistance) {
                            hitDistance = distance
                            hitId = node.id
                        }
                    }
                    hitId?.let(onNodeClick)
                }
            }
    ) {
        val width = size.width
        val height = size.height
        graph.edges.forEach { edge ->
            val from = graph.nodes.getOrNull(edge.fromIndex) ?: return@forEach
            val to = graph.nodes.getOrNull(edge.toIndex) ?: return@forEach
            val start = toScreen(from.x, from.y, width, height, scale, offset)
            val end = toScreen(to.x, to.y, width, height, scale, offset)
            drawLine(
                color = edgeColor.copy(
                    alpha = (0.12f + 0.5f * edge.similarity).coerceIn(0.1f, 0.7f)
                ),
                start = start,
                end = end,
                strokeWidth = (1f + 2f * edge.similarity) * densityScale
            )
        }
        graph.nodes.forEach { node ->
            val center = toScreen(node.x, node.y, width, height, scale, offset)
            val radius = radiusFor(node.degree, baseRadiusPx, radiusStepPx, maxRadiusPx)
            drawCircle(
                color = ringColor,
                radius = radius + 1.5f * densityScale,
                center = center
            )
            drawCircle(
                color = if (node.degree >= HUB_DEGREE) hubColor else nodeColor,
                radius = radius,
                center = center
            )
            if (showAllLabels || node.degree >= 1) {
                drawContext.canvas.nativeCanvas.drawText(
                    node.title.take(LABEL_CHARS),
                    center.x,
                    center.y + radius + labelGapPx + labelSizePx * 0.35f,
                    labelPaint
                )
            }
        }
    }
}

@Composable
private fun GraphLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.graph_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GraphMessageState(
    icon: ImageVector,
    titleRes: Int,
    bodyRes: Int,
    actionTextRes: Int? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(MoshiShapeLarge)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionTextRes != null && onAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            TextButton(onClick = onAction) {
                Text(stringResource(actionTextRes))
            }
        }
    }
}

private fun radiusFor(degree: Int, base: Float, step: Float, max: Float): Float =
    (base + degree * step).coerceAtMost(max)

private fun toScreen(
    normalizedX: Float,
    normalizedY: Float,
    width: Float,
    height: Float,
    scale: Float,
    offset: Offset
): Offset {
    val padding = minOf(width, height) * GRAPH_PADDING_RATIO
    val baseX = padding + normalizedX * (width - 2f * padding)
    val baseY = padding + normalizedY * (height - 2f * padding)
    val centerX = width / 2f
    val centerY = height / 2f
    return Offset(
        centerX + (baseX - centerX) * scale + offset.x,
        centerY + (baseY - centerY) * scale + offset.y
    )
}

private fun sampleGraphUiState(): GraphViewModel.GraphUiState {
    val nodes = listOf(
        GraphNode("1", "向量检索笔记", NoteEntity.TYPE_TEXT, 3, 0.28f, 0.34f),
        GraphNode("2", "机器学习讲义", NoteEntity.TYPE_PDF, 2, 0.52f, 0.24f),
        GraphNode("3", "白板讨论截图", NoteEntity.TYPE_IMAGE_OCR, 1, 0.72f, 0.46f),
        GraphNode("4", "组会语音记录", NoteEntity.TYPE_AUDIO, 2, 0.44f, 0.62f),
        GraphNode("5", "端侧推理方案", NoteEntity.TYPE_TEXT, 2, 0.22f, 0.72f)
    )
    val edges = listOf(
        GraphEdge(0, 1, 0.74f),
        GraphEdge(0, 4, 0.68f),
        GraphEdge(1, 2, 0.61f),
        GraphEdge(3, 4, 0.57f),
        GraphEdge(0, 3, 0.52f)
    )
    return GraphViewModel.GraphUiState(
        stage = GraphStage.Ready,
        graph = KnowledgeGraph(nodes = nodes, edges = edges),
        scanned = 5,
        analyzed = 5,
        ignored = 0
    )
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun GraphPageScreenPreview() {
    MoshiTheme {
        GraphPageScreen(
            uiState = sampleGraphUiState(),
            onBack = {},
            onNodeClick = {},
            onEvent = {},
            onOpenModel = {}
        )
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun GraphPageEmptyScreenPreview() {
    MoshiTheme {
        GraphPageScreen(
            uiState = GraphViewModel.GraphUiState(stage = GraphStage.Empty),
            onBack = {},
            onNodeClick = {},
            onEvent = {},
            onOpenModel = {}
        )
    }
}
