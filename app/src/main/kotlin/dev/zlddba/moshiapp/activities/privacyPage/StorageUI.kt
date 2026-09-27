package dev.zlddba.moshiapp.activities.privacyPage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

private data class StorageStat(
    val labelRes: Int,
    val valueRes: Int
)

private val storageStats = listOf(
    StorageStat(
        labelRes = R.string.storage_stat_notes,
        valueRes = R.string.storage_stat_notes_value
    ),
    StorageStat(
        labelRes = R.string.storage_stat_chunks,
        valueRes = R.string.storage_stat_chunks_value
    ),
    StorageStat(
        labelRes = R.string.storage_stat_size,
        valueRes = R.string.storage_stat_size_value
    )
)

@Composable
fun StoragePageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var clearDialogVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        PageTopBar(
            titleRes = R.string.storage_title,
            backDescRes = R.string.storage_back,
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.storage_stats_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            shape = MoshiShapeMedium,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            ),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                storageStats.forEachIndexed { index, stat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(stat.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(stat.valueRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (index < storageStats.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.storage_export),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        StorageActionCard(
            titleRes = R.string.storage_export,
            descRes = R.string.storage_export_desc,
            icon = Icons.Outlined.Save,
            onClick = {}
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.storage_clear),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        StorageActionCard(
            titleRes = R.string.storage_clear,
            descRes = R.string.storage_clear_desc,
            icon = Icons.Outlined.Delete,
            danger = true,
            onClick = { clearDialogVisible = true }
        )
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (clearDialogVisible) {
        AlertDialog(
            onDismissRequest = { clearDialogVisible = false },
            title = { Text(stringResource(R.string.storage_clear)) },
            text = { Text(stringResource(R.string.storage_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { clearDialogVisible = false }) {
                    Text(stringResource(R.string.storage_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDialogVisible = false }) {
                    Text(stringResource(R.string.storage_cancel))
                }
            }
        )
    }
}

@Composable
private fun StorageActionCard(
    titleRes: Int,
    descRes: Int,
    icon: ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (danger) MaterialTheme.colorScheme.secondary
    else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (danger) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = stringResource(descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun StoragePageScreenPreview() {
    MoshiTheme {
        StoragePageScreen(onBack = {})
    }
}