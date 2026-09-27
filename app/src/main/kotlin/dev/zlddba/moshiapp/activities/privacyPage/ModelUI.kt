package dev.zlddba.moshiapp.activities.privacyPage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.School
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiShapeSmall
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

private data class ModelItem(
    val icon: ImageVector,
    val nameRes: Int,
    val sizeRes: Int,
    val statusRes: Int,
    val installed: Boolean,
    val current: Boolean
)

private val generationModels = listOf(
    ModelItem(
        icon = Icons.Outlined.School,
        nameRes = R.string.model_name_gemma,
        sizeRes = R.string.model_size_gemma,
        statusRes = R.string.model_status_ready,
        installed = true,
        current = true
    ),
    ModelItem(
        icon = Icons.Outlined.Book,
        nameRes = R.string.model_name_qwen,
        sizeRes = R.string.model_size_qwen,
        statusRes = R.string.model_status_not_installed,
        installed = false,
        current = false
    )
)

private val embeddingModels = listOf(
    ModelItem(
        icon = Icons.Outlined.CloudSync,
        nameRes = R.string.model_name_embed,
        sizeRes = R.string.model_size_embed,
        statusRes = R.string.model_status_downloading,
        installed = true,
        current = true
    )
)

@Composable
fun ModelPageScreen(
    uiState: ModelViewModel.ModelUiState,
    onEvent: (ModelViewModel.ModelEvent) -> Unit,
    onBack: () -> Unit,
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
            titleRes = R.string.model_title,
            backDescRes = R.string.model_back,
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = MoshiShapeMedium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.model_low_end_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(14.dp),
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        ModelGroup(
            titleRes = R.string.model_group_llm,
            models = generationModels
        )
        Spacer(modifier = Modifier.height(20.dp))
        ModelGroup(
            titleRes = R.string.model_group_embed,
            models = embeddingModels
        )
        Spacer(modifier = Modifier.height(20.dp))
        Column {
            GroupLabel(titleRes = R.string.model_group_asr)
            AsrModelCard(uiState = uiState, onEvent = onEvent)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ModelGroup(
    titleRes: Int,
    models: List<ModelItem>
) {
    Column {
        GroupLabel(titleRes = titleRes)
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            models.forEach { model ->
                ModelCard(model = model)
            }
        }
    }
}

@Composable
private fun GroupLabel(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun ModelCard(model: ModelItem) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (model.current) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MoshiShapeSmall,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = model.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(model.nameRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (model.current) {
                            Surface(
                                shape = MoshiShapePill,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.model_current_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp,
                                        vertical = 2.dp
                                    )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row {
                        Text(
                            text = stringResource(model.sizeRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(model.statusRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (model.installed) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                if (model.current) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        shape = MoshiShapeSmall
                    ) {
                        Text(stringResource(R.string.model_delete))
                    }
                } else if (model.installed) {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        shape = MoshiShapeSmall
                    ) {
                        Text(stringResource(R.string.model_switch))
                    }
                } else {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        shape = MoshiShapeSmall
                    ) {
                        Text(stringResource(R.string.model_download))
                    }
                }
            }
        }
    }
}

@Composable
private fun AsrModelCard(
    uiState: ModelViewModel.ModelUiState,
    onEvent: (ModelViewModel.ModelEvent) -> Unit
) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (uiState.asrReady) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MoshiShapeSmall,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.model_name_asr),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row {
                        Text(
                            text = stringResource(R.string.model_size_asr),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                uiState.asrDownloading -> stringResource(
                                    R.string.model_status_downloading_pct,
                                    uiState.asrProgress
                                )

                                uiState.asrReady -> stringResource(R.string.model_status_ready)
                                else -> stringResource(R.string.model_status_not_installed)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                uiState.asrDownloading -> MaterialTheme.colorScheme.primary
                                uiState.asrReady -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                }
            }
            if (uiState.asrDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { uiState.asrProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                if (uiState.asrDownloading) {
                    OutlinedButton(
                        onClick = { onEvent(ModelViewModel.ModelEvent.AsrCancelClicked) },
                        modifier = Modifier.weight(1f),
                        shape = MoshiShapeSmall
                    ) {
                        Text(stringResource(R.string.model_asr_cancel))
                    }
                } else if (uiState.asrReady) {
                    OutlinedButton(
                        onClick = { onEvent(ModelViewModel.ModelEvent.AsrDeleteClicked) },
                        modifier = Modifier.weight(1f),
                        shape = MoshiShapeSmall
                    ) {
                        Text(stringResource(R.string.model_delete))
                    }
                } else {
                    Button(
                        onClick = { onEvent(ModelViewModel.ModelEvent.AsrDownloadClicked) },
                        modifier = Modifier.weight(1f),
                        shape = MoshiShapeSmall
                    ) {
                        Text(stringResource(R.string.model_download))
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun ModelPageScreenPreview() {
    MoshiTheme {
        ModelPageScreen(
            uiState = ModelViewModel.ModelUiState(asrReady = true),
            onEvent = {},
            onBack = {}
        )
    }
}