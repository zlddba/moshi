package dev.zlddba.moshiapp.activities.privacyPage

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.ingest.models.ModelCatalog
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiShapeSmall
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

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
        ModelGroup(titleRes = R.string.model_group_llm) {
            ManagedModelCard(
                modelId = ModelCatalog.GEMMA,
                iconRes = R.drawable.gemmaicon,
                nameRes = R.string.model_name_gemma,
                versionRes = R.string.model_version_gemma,
                sizeRes = R.string.model_size_gemma,
                state = uiState.gemma,
                current = uiState.currentLlm == ModelCatalog.GEMMA,
                switchable = true,
                onEvent = onEvent
            )
            Spacer(modifier = Modifier.height(10.dp))
            ManagedModelCard(
                modelId = ModelCatalog.QWEN,
                iconRes = R.drawable.qwicons,
                nameRes = R.string.model_name_qwen,
                versionRes = R.string.model_version_qwen,
                sizeRes = R.string.model_size_qwen,
                state = uiState.qwen,
                current = uiState.currentLlm == ModelCatalog.QWEN,
                switchable = true,
                onEvent = onEvent
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        ModelGroup(titleRes = R.string.model_group_embed) {
            ManagedModelCard(
                modelId = ModelCatalog.GECKO,
                iconRes = R.drawable.geckoicon,
                nameRes = R.string.model_name_embed,
                versionRes = R.string.model_version_embed,
                sizeRes = R.string.model_size_embed,
                state = uiState.gecko,
                current = false,
                switchable = false,
                onEvent = onEvent
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        ModelGroup(titleRes = R.string.model_group_asr) {
            ManagedModelCard(
                modelId = ModelCatalog.SENSE_VOICE,
                iconRes = R.drawable.sensevoiceicon,
                nameRes = R.string.model_name_asr,
                versionRes = R.string.model_version_asr,
                sizeRes = R.string.model_size_asr,
                state = uiState.asr,
                current = false,
                switchable = false,
                onEvent = onEvent
            )
            Spacer(modifier = Modifier.height(10.dp))
            ManagedModelCard(
                modelId = ModelCatalog.STREAM_ASR,
                iconRes = R.drawable.sensevoiceicon,
                nameRes = R.string.model_name_stream_asr,
                versionRes = R.string.model_version_stream_asr,
                sizeRes = R.string.model_size_stream_asr,
                state = uiState.streamAsr,
                current = false,
                switchable = false,
                onEvent = onEvent
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ModelGroup(
    titleRes: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        GroupLabel(titleRes = titleRes)
        Column(content = content)
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
private fun ManagedModelCard(
    modelId: String,
    @DrawableRes iconRes: Int = 0,
    nameRes: Int,
    versionRes: Int,
    sizeRes: Int,
    state: ModelViewModel.ModelCardState,
    current: Boolean,
    switchable: Boolean,
    onEvent: (ModelViewModel.ModelEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (current) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MoshiShapeSmall,
                    color = Color.Transparent
                ) {
                    if (iconRes != 0) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(nameRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (current) {
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
                            text = stringResource(
                                R.string.model_version_fmt,
                                stringResource(versionRes)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.ready) {
                                stringResource(
                                    R.string.model_occupied_fmt,
                                    formatBytes(state.bytesOnDisk)
                                )
                            } else {
                                stringResource(sizeRes)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            state.downloading -> stringResource(
                                R.string.model_status_downloading_pct,
                                state.progress
                            )

                            state.ready -> stringResource(R.string.model_status_ready)
                            else -> stringResource(R.string.model_status_not_installed)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            state.downloading -> MaterialTheme.colorScheme.primary
                            state.ready -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }
            if (state.downloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            CardActions(
                modelId = modelId,
                state = state,
                current = current,
                switchable = switchable,
                onEvent = onEvent
            )
        }
    }
}

@Composable
private fun CardActions(
    modelId: String,
    state: ModelViewModel.ModelCardState,
    current: Boolean,
    switchable: Boolean,
    onEvent: (ModelViewModel.ModelEvent) -> Unit
) {
    when {
        state.downloading -> OutlinedButton(
            onClick = { onEvent(ModelViewModel.ModelEvent.Cancel(modelId)) },
            modifier = Modifier.fillMaxWidth(),
            shape = MoshiShapeSmall
        ) {
            Text(stringResource(R.string.model_cancel_download))
        }

        !state.ready -> Button(
            onClick = { onEvent(ModelViewModel.ModelEvent.Download(modelId)) },
            modifier = Modifier.fillMaxWidth(),
            shape = MoshiShapeSmall
        ) {
            Text(stringResource(R.string.model_download))
        }

        else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (switchable && !current) {
                Button(
                    onClick = { onEvent(ModelViewModel.ModelEvent.SwitchLlm(modelId)) },
                    modifier = Modifier.weight(1f),
                    shape = MoshiShapeSmall
                ) {
                    Text(stringResource(R.string.model_switch))
                }
            }
            OutlinedButton(
                onClick = { onEvent(ModelViewModel.ModelEvent.Delete(modelId)) },
                modifier = Modifier.weight(1f),
                shape = MoshiShapeSmall
            ) {
                Text(stringResource(R.string.model_delete))
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1_000_000.0)
    bytes > 0L -> "%.0f kB".format(bytes / 1_000.0)
    else -> "0 B"
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun ModelPageScreenPreview() {
    MoshiTheme {
        ModelPageScreen(
            uiState = ModelViewModel.ModelUiState(
                gemma = ModelViewModel.ModelCardState(
                    ready = true,
                    bytesOnDisk = 2_588_147_712L
                ),
                qwen = ModelViewModel.ModelCardState(),
                gecko = ModelViewModel.ModelCardState(
                    ready = true,
                    bytesOnDisk = 114_935_530L
                ),
                asr = ModelViewModel.ModelCardState(
                    ready = true,
                    bytesOnDisk = 239_549_735L
                ),
                currentLlm = ModelCatalog.GEMMA
            ),
            onEvent = {},
            onBack = {}
        )
    }
}
