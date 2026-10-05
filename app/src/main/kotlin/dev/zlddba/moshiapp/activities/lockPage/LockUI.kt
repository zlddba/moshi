package dev.zlddba.moshiapp.activities.lockPage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.lockPage.LockViewModel.LockEvent
import dev.zlddba.moshiapp.activities.lockPage.LockViewModel.Stage
import dev.zlddba.moshiapp.data.prefs.SecurityPrefs
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import kotlin.math.hypot

private val GRID_SIZE = 250.dp
private const val GRID_CELLS = 3
private const val GRID_POINTS = GRID_CELLS * GRID_CELLS

@Composable
fun LockPageScreen(
    uiState: LockViewModel.LockUiState,
    onEvent: (LockEvent) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(56.dp))
        Text(
            text = stringResource(uiState.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = uiState.messageRes?.let { stringResource(it) }
                ?: stringResource(R.string.lock_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = if (uiState.error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        if (uiState.usesPin) {
            PinDots(filled = uiState.input.length)
            Spacer(modifier = Modifier.height(32.dp))
            PinKeypad(
                onDigit = { onEvent(LockEvent.Digit(it)) },
                onDelete = { onEvent(LockEvent.Delete) }
            )
        } else {
            PatternGrid(
                selected = uiState.pattern,
                onDrawn = { onEvent(LockEvent.PatternDrawn(it)) }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (uiState.stage == Stage.Unlock) {
            if (uiState.biometricEnabled && uiState.biometricAvailable) {
                OutlinedButton(
                    onClick = { onEvent(LockEvent.BiometricRequested) },
                    shape = MoshiShapePill
                ) {
                    Text(stringResource(R.string.lock_use_biometric))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (uiState.method != SecurityPrefs.METHOD_NONE) {
                TextButton(onClick = { onEvent(LockEvent.ForgotCredential) }) {
                    Text(stringResource(R.string.lock_forgot))
                }
            }
        } else {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.lock_cancel))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(LockViewModel.PIN_LENGTH) { index ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = if (index < filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = MoshiShapePill
                    )
            )
        }
    }
}

@Composable
private fun PinKeypad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", KEY_DELETE)
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { key ->
                    PinKey(
                        label = key,
                        onClick = {
                            when (key) {
                                "" -> Unit
                                KEY_DELETE -> onDelete()
                                else -> onDigit(key)
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    val visible = label.isNotEmpty()
    Surface(
        onClick = { if (visible) onClick() },
        enabled = visible,
        shape = MoshiShapePill,
        color = if (visible) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        modifier = Modifier.size(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (visible) {
                Text(
                    text = if (label == KEY_DELETE) stringResource(R.string.lock_delete)
                    else label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PatternGrid(
    selected: List<Int>,
    onDrawn: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val sizePx = with(density) { GRID_SIZE.toPx() }
    val dotRadius = with(density) { 11.dp.toPx() }
    val hitRadius = with(density) { 30.dp.toPx() }
    val lineWidth = with(density) { 6.dp.toPx() }

    val activeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

    var drawing by remember { mutableStateOf<List<Int>>(emptyList()) }
    var finger by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(selected) {
        if (selected.isEmpty()) {
            drawing = emptyList()
            finger = null
        }
    }

    Box(
        modifier = modifier
            .size(GRID_SIZE)
            .pointerInput(sizePx) {
                detectDragGestures(
                    onDragStart = { position ->
                        val centers = patternCenters(sizePx)
                        val index = hitIndex(centers, position, hitRadius)
                        drawing = if (index == null) emptyList() else listOf(index)
                        finger = position
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        finger = change.position
                        val centers = patternCenters(sizePx)
                        val index = hitIndex(centers, change.position, hitRadius)
                        if (index != null && index !in drawing) {
                            drawing = drawing + index
                        }
                    },
                    onDragEnd = {
                        finger = null
                        onDrawn(drawing)
                    },
                    onDragCancel = {
                        finger = null
                        drawing = emptyList()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centers = patternCenters(size.width)
            for (index in 0 until drawing.size - 1) {
                drawLine(
                    color = lineColor,
                    start = centers[drawing[index]],
                    end = centers[drawing[index + 1]],
                    strokeWidth = lineWidth
                )
            }
            val last = drawing.lastOrNull()
            val currentFinger = finger
            if (last != null && currentFinger != null) {
                drawLine(
                    color = lineColor,
                    start = centers[last],
                    end = currentFinger,
                    strokeWidth = lineWidth
                )
            }
            centers.forEachIndexed { index, center ->
                val active = index in drawing
                drawCircle(
                    color = if (active) activeColor else idleColor,
                    radius = if (active) dotRadius * 1.25f else dotRadius,
                    center = center
                )
            }
        }
    }
}

private fun patternCenters(sizePx: Float): List<Offset> {
    val cell = sizePx / GRID_CELLS
    return (0 until GRID_POINTS).map { index ->
        Offset(
            x = cell * ((index % GRID_CELLS) + 0.5f),
            y = cell * ((index / GRID_CELLS) + 0.5f)
        )
    }
}

private fun hitIndex(centers: List<Offset>, position: Offset, radius: Float): Int? {
    var best: Int? = null
    var bestDistance = Float.MAX_VALUE
    centers.forEachIndexed { index, center ->
        val distance = hypot(center.x - position.x, center.y - position.y)
        if (distance <= radius && distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}

private const val KEY_DELETE = "DEL"

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun LockPagePinPreview() {
    MoshiTheme {
        LockPageScreen(
            uiState = LockViewModel.LockUiState(
                stage = Stage.Unlock,
                method = SecurityPrefs.METHOD_PIN,
                input = "123"
            ),
            onEvent = {},
            onCancel = {}
        )
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun LockPagePatternPreview() {
    MoshiTheme {
        LockPageScreen(
            uiState = LockViewModel.LockUiState(
                stage = Stage.SetupPattern,
                method = SecurityPrefs.METHOD_NONE,
                pattern = listOf(0, 1, 4)
            ),
            onEvent = {},
            onCancel = {}
        )
    }
}
