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
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ModelGroup(
    titleRes: Int,
    models: List<ModelItem>
) {
    Column {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            models.forEach { model ->
                ModelCard(model = model)
            }
        }
    }
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
@Preview(showBackground = true, showSystemUi = true)
private fun ModelPageScreenPreview() {
    MoshiTheme {
        ModelPageScreen(onBack = {})
    }
}