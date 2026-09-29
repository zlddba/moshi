package dev.zlddba.moshiapp.activities.mainPage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.ui.theme.MoshiShapeLarge
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

private data class MineAction(
    val titleRes: Int,
    val onClick: () -> Unit
)

@Composable
fun MainSettingsScreen(
    engineMode: Int,
    onEngineModeSelect: (Int) -> Unit,
    onCloudConfig: () -> Unit,
    onModelManage: () -> Unit,
    onPrivacy: () -> Unit,
    onStorage: () -> Unit,
    onLicense: () -> Unit,
    onPolicy: () -> Unit,
    onHelp: () -> Unit,
    telemetry: Boolean,
    onTelemetryChange: (Boolean) -> Unit,
    onPlaceholder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var forceLocal by rememberSaveable { mutableStateOf(true) }
    var encrypt by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.mine_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        MineStatusCard()
        Spacer(modifier = Modifier.height(20.dp))

        MineGroupTitle(titleRes = R.string.mine_group_engine)
        MineGroup {
            EngineModeRow(
                selectedIndex = engineMode,
                onSelect = onEngineModeSelect
            )
            MineActionRow(
                titleRes = R.string.mine_cloud_config,
                icon = Icons.Outlined.CloudSync,
                onClick = onCloudConfig
            )
            MineActionRow(
                titleRes = R.string.mine_model_manage,
                icon = Icons.Outlined.Shield,
                onClick = onModelManage
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        MineGroupTitle(titleRes = R.string.mine_group_privacy)
        MineGroup {
            MineSwitchRow(
                titleRes = R.string.mine_privacy_force_local,
                checked = forceLocal,
                onCheckedChange = { forceLocal = it }
            )
            MineSwitchRow(
                titleRes = R.string.mine_privacy_encrypt,
                checked = encrypt,
                onCheckedChange = { encrypt = it }
            )
            MineSwitchRow(
                titleRes = R.string.mine_privacy_telemetry,
                checked = telemetry,
                onCheckedChange = onTelemetryChange
            )
            MineActionRow(
                titleRes = R.string.mine_privacy_entry,
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                showIcon = false,
                onClick = onPrivacy
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        MineGroupTitle(titleRes = R.string.mine_group_data)
        MineGroup {
            MineActionRow(
                titleRes = R.string.mine_storage_stats,
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                showIcon = false,
                onClick = onStorage
            )
            MineActionRow(
                titleRes = R.string.mine_storage_export,
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                showIcon = false,
                onClick = onPlaceholder
            )
            MineActionRow(
                titleRes = R.string.mine_storage_clear,
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                showIcon = false,
                onClick = onPlaceholder
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        MineGroupTitle(titleRes = R.string.mine_group_about)
        MineGroup {
            MineActionRow(
                titleRes = R.string.mine_about_help,
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                showIcon = false,
                onClick = onHelp
            )
            MineActionRow(
                titleRes = R.string.mine_about_version,
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                showIcon = false,
                value = "1.0.0",
                onClick = onPlaceholder
            )
            MineActionRow(
                titleRes = R.string.mine_about_license,
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                showIcon = false,
                onClick = onLicense
            )
            MineActionRow(
                titleRes = R.string.mine_about_privacy,
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                showIcon = false,
                onClick = onPolicy
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun MineStatusCard() {
    Surface(
        shape = MoshiShapeLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
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
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.mine_status_local),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.mine_status_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun MineGroupTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun MineGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun MineActionRow(
    titleRes: Int,
    icon: ImageVector,
    showIcon: Boolean = true,
    value: String? = null,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showIcon) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun MineSwitchRow(
    titleRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EngineModeRow(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val modeLabels = listOf(
        R.string.mine_engine_mode_local,
        R.string.mine_engine_mode_hybrid,
        R.string.mine_engine_mode_cloud
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = stringResource(R.string.mine_engine_mode),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modeLabels.forEachIndexed { index, labelRes ->
                val selected = index == selectedIndex
                Surface(
                    onClick = { onSelect(index) },
                    shape = MoshiShapePill,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun MainSettingsScreenPreview() {
    MoshiTheme {
        MainSettingsScreen(
            engineMode = 1,
            onEngineModeSelect = {},
            onCloudConfig = {},
            onModelManage = {},
            onPrivacy = {},
            onStorage = {},
            onLicense = {},
            onPolicy = {},
            onHelp = {},
            telemetry = true,
            onTelemetryChange = {},
            onPlaceholder = {}
        )
    }
}
