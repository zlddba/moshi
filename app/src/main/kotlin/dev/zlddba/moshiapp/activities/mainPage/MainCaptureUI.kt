package dev.zlddba.moshiapp.activities.mainPage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

private data class CaptureEntry(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
    val accent: Int,
    val onClick: () -> Unit
)

@Composable
fun MainCaptureScreen(
    onTextClick: () -> Unit,
    onFileClick: () -> Unit,
    onOcrClick: () -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = listOf(
        CaptureEntry(
            icon = Icons.AutoMirrored.Outlined.TextSnippet,
            titleRes = R.string.capture_text_title,
            descRes = R.string.capture_text_desc,
            accent = 0,
            onClick = onTextClick
        ),
        CaptureEntry(
            icon = Icons.Outlined.Description,
            titleRes = R.string.capture_file_title,
            descRes = R.string.capture_file_desc,
            accent = 1,
            onClick = onFileClick
        ),
        CaptureEntry(
            icon = Icons.Outlined.Image,
            titleRes = R.string.capture_ocr_title,
            descRes = R.string.capture_ocr_desc,
            accent = 2,
            onClick = onOcrClick
        ),
        CaptureEntry(
            icon = Icons.Outlined.Mic,
            titleRes = R.string.capture_voice_title,
            descRes = R.string.capture_voice_desc,
            accent = 3,
            onClick = onVoiceClick
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.capture_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.capture_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            entries.chunked(2).forEach { rowEntries ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowEntries.forEach { entry ->
                        CaptureEntryCard(
                            entry = entry,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureEntryCard(
    entry: CaptureEntry,
    modifier: Modifier = Modifier
) {
    val tileColor = when (entry.accent) {
        1 -> MaterialTheme.colorScheme.secondaryContainer
        2 -> MaterialTheme.colorScheme.tertiaryContainer
        3 -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.primary
    }
    val tileTint = when (entry.accent) {
        1 -> MaterialTheme.colorScheme.onSecondaryContainer
        2 -> MaterialTheme.colorScheme.onTertiaryContainer
        3 -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Surface(
        onClick = entry.onClick,
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(MoshiShapeMedium)
                    .background(tileColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = tileTint
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(entry.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(entry.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun MainCaptureScreenPreview() {
    MoshiTheme {
        MainCaptureScreen(
            onTextClick = {},
            onFileClick = {},
            onOcrClick = {},
            onVoiceClick = {}
        )
    }
}
