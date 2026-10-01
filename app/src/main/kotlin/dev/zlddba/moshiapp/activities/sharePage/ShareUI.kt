package dev.zlddba.moshiapp.activities.sharePage

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.activities.textPage.TitleField
import dev.zlddba.moshiapp.data.repo.IngestRepository
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

@Composable
fun SharePageScreen(
    uiState: ShareViewModel.ShareUiState,
    onEvent: (ShareViewModel.ShareEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        PageTopBar(
            titleRes = R.string.share_title,
            backDescRes = R.string.share_back,
            onBack = onClose
        )
        if (uiState.preparingImage) {
            SharePreparingState()
        } else {
            ShareContent(
                uiState = uiState,
                onEvent = onEvent,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun SharePreparingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.share_image_preparing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ShareContent(
    uiState: ShareViewModel.ShareUiState,
    onEvent: (ShareViewModel.ShareEvent) -> Unit,
    onClose: () -> Unit
) {
    val blocked = uiState.importing || uiState.generating || uiState.unsupported

    Spacer(modifier = Modifier.height(8.dp))
    ShareFileCard(uiState = uiState)
    Spacer(modifier = Modifier.height(16.dp))
    TitleField(
        title = uiState.title,
        generating = uiState.generating,
        onTitleChange = { onEvent(ShareViewModel.ShareEvent.TitleChanged(it)) },
        onRegenerate = { onEvent(ShareViewModel.ShareEvent.RegenerateTitle) }
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = uiState.tags,
        onValueChange = { onEvent(ShareViewModel.ShareEvent.TagsChanged(it)) },
        singleLine = true,
        label = { Text(stringResource(R.string.tag_label)) },
        placeholder = { Text(stringResource(R.string.tag_input_hint)) },
        shape = MoshiShapeMedium,
        modifier = Modifier.fillMaxWidth()
    )
    if (uiState.unsupported) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.share_unsupported_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    val stage = uiState.stage
    if (uiState.importing) {
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (stage == null) {
                stringResource(R.string.share_importing)
            } else {
                stageLabel(stage)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onClose,
            shape = MoshiShapeMedium,
            enabled = !uiState.importing,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.detail_cancel))
        }
        Button(
            onClick = { onEvent(ShareViewModel.ShareEvent.Confirm) },
            shape = MoshiShapeMedium,
            enabled = !blocked,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.share_confirm))
        }
    }
    Spacer(modifier = Modifier.height(32.dp))
}

@Composable
private fun ShareFileCard(uiState: ShareViewModel.ShareUiState) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MoshiShapeMedium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Article,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.fileName.ifBlank { stringResource(R.string.share_unknown_file) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.mimeType ?: stringResource(R.string.share_unknown_mime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun stageLabel(stage: IngestRepository.Stage): String = when (stage) {
    IngestRepository.Stage.Parsing -> stringResource(R.string.capture_import_parsing)
    IngestRepository.Stage.Chunking -> stringResource(R.string.capture_import_chunking)
    IngestRepository.Stage.Writing -> stringResource(R.string.capture_import_writing)
    is IngestRepository.Stage.Ocring ->
        stringResource(R.string.capture_import_ocring, stage.page, stage.total)
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun SharePageScreenPreview() {
    MoshiTheme {
        SharePageScreen(
            uiState = ShareViewModel.ShareUiState(
                fileName = "华北五省参赛说明.pdf",
                mimeType = "application/pdf",
                title = "参赛说明",
                tags = "比赛, 资料",
                preparingImage = false
            ),
            onEvent = {},
            onClose = {}
        )
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun SharePagePreparingPreview() {
    MoshiTheme {
        SharePageScreen(
            uiState = ShareViewModel.ShareUiState(preparingImage = true),
            onEvent = {},
            onClose = {}
        )
    }
}
