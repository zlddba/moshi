package dev.zlddba.moshiapp.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val CHIP_PADDING_DP = 24
private const val WIDE_CHAR_DP = 13
private const val NARROW_CHAR_DP = 7
private const val CHIP_GAP_DP = 6

@Composable
fun TagChips(
    tags: List<String>,
    modifier: Modifier = Modifier,
    onClick: ((String) -> Unit)? = null
) {
    if (tags.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val rows = packRows(tags, maxWidth.value.toInt())
        Column(
            verticalArrangement = Arrangement.spacedBy(CHIP_GAP_DP.dp)
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP_DP.dp)) {
                    row.forEach { tag ->
                        TagChip(tag = tag, onClick = onClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(tag: String, onClick: ((String) -> Unit)?) {
    val label: @Composable () -> Unit = {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
    if (onClick == null) {
        Surface(shape = MoshiShapeSmall, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            label()
        }
    } else {
        Surface(
            onClick = { onClick(tag) },
            shape = MoshiShapeSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            label()
        }
    }
}

private fun packRows(tags: List<String>, budget: Int): List<List<String>> {
    if (budget <= 0) return listOf(tags)
    val rows = ArrayList<MutableList<String>>()
    var current = ArrayList<String>()
    var used = 0
    for (tag in tags) {
        val width = estimateWidth(tag)
        if (current.isNotEmpty() && used + width > budget) {
            rows.add(current)
            current = ArrayList()
            used = 0
        }
        current.add(tag)
        used += width + CHIP_GAP_DP
    }
    if (current.isNotEmpty()) rows.add(current)
    return rows
}

private fun estimateWidth(tag: String): Int {
    var width = CHIP_PADDING_DP
    for (ch in tag) {
        width += if (isWide(ch)) WIDE_CHAR_DP else NARROW_CHAR_DP
    }
    return width
}

private fun isWide(ch: Char): Boolean {
    val code = ch.code
    return code in 0x1100..0x115F ||
        code in 0x2E80..0xA4CF ||
        code in 0xAC00..0xD7A3 ||
        code in 0xF900..0xFAFF ||
        code in 0xFE30..0xFE6F ||
        code in 0xFF00..0xFF60 ||
        code in 0xFFE0..0xFFE6
}
