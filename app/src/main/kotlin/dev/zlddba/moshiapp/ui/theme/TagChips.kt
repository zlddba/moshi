package dev.zlddba.moshiapp.ui.theme

import androidx.compose.foundation.layout.Arrangement
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

private const val ROW_BUDGET_DP = 280
private const val PER_CHAR_DP = 14

@Composable
fun TagChips(
    tags: List<String>,
    modifier: Modifier = Modifier,
    onClick: ((String) -> Unit)? = null
) {
    if (tags.isEmpty()) return
    val rows = packRows(tags, ROW_BUDGET_DP)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { tag ->
                    TagChip(tag = tag, onClick = onClick)
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
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
    if (onClick == null) {
        Surface(shape = MoshiShapeSmall, color = MaterialTheme.colorScheme.tertiaryContainer) {
            label()
        }
    } else {
        Surface(
            onClick = { onClick(tag) },
            shape = MoshiShapeSmall,
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            label()
        }
    }
}

private fun packRows(tags: List<String>, budget: Int): List<List<String>> {
    val rows = ArrayList<MutableList<String>>()
    var current = ArrayList<String>()
    var used = 0
    for (tag in tags) {
        val width = tag.length * PER_CHAR_DP + 24
        if (current.isNotEmpty() && used + width > budget) {
            rows.add(current)
            current = ArrayList()
            used = 0
        }
        current.add(tag)
        used += width + 6
    }
    if (current.isNotEmpty()) rows.add(current)
    return rows
}
