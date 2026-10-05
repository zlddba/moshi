package dev.zlddba.moshiapp.activities.privacyPage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.data.prefs.SecurityPrefs
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

@Composable
fun PrivacyPageScreen(
    uiState: PrivacyViewModel.PrivacyUiState,
    onBack: () -> Unit,
    onEvent: (PrivacyViewModel.PrivacyEvent) -> Unit,
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
            titleRes = R.string.privacy_title,
            backDescRes = R.string.privacy_back,
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(8.dp))
        PrivacySwitchCard(
            titleRes = R.string.privacy_encrypt,
            descRes = R.string.privacy_encrypt_desc,
            stateRes = if (uiState.storageEncrypted) {
                R.string.privacy_storage_state_encrypted
            } else {
                R.string.privacy_storage_state_plain
            },
            checked = uiState.encryptionEnabled,
            enabled = !uiState.busy,
            onCheckedChange = {
                onEvent(PrivacyViewModel.PrivacyEvent.EncryptionChanged(it))
            }
        )
        if (uiState.busy) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.privacy_converting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        PrivacySwitchCard(
            titleRes = R.string.privacy_force_local,
            descRes = R.string.privacy_force_local_desc,
            stateRes = null,
            checked = uiState.forceLocal,
            enabled = true,
            onCheckedChange = {
                onEvent(PrivacyViewModel.PrivacyEvent.ForceLocalChanged(it))
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        PrivacySwitchCard(
            titleRes = R.string.privacy_qa_history,
            descRes = R.string.privacy_qa_history_desc,
            stateRes = null,
            checked = uiState.qaHistoryEnabled,
            enabled = true,
            onCheckedChange = {
                onEvent(PrivacyViewModel.PrivacyEvent.QaHistoryChanged(it))
            }
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.privacy_lock_section),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        LockCard(uiState = uiState, onEvent = onEvent)
        Spacer(modifier = Modifier.height(12.dp))
        PrivacySwitchCard(
            titleRes = R.string.privacy_biometric,
            descRes = R.string.privacy_biometric_desc,
            stateRes = if (!uiState.biometricAvailable) {
                R.string.privacy_biometric_unavailable
            } else {
                null
            },
            checked = uiState.biometricEnabled,
            enabled = uiState.lockConfigured && uiState.biometricAvailable,
            onCheckedChange = {
                onEvent(PrivacyViewModel.PrivacyEvent.BiometricChanged(it))
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        PrivacySwitchCard(
            titleRes = R.string.privacy_autolock,
            descRes = R.string.privacy_autolock_desc,
            stateRes = null,
            checked = uiState.autoLockEnabled,
            enabled = uiState.lockConfigured,
            onCheckedChange = {
                onEvent(PrivacyViewModel.PrivacyEvent.AutoLockChanged(it))
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.privacy_encrypt_scope),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun LockCard(
    uiState: PrivacyViewModel.PrivacyUiState,
    onEvent: (PrivacyViewModel.PrivacyEvent) -> Unit
) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(uiState.lockStateRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onEvent(PrivacyViewModel.PrivacyEvent.SetLockPin) },
                    shape = MoshiShapePill
                ) {
                    Text(
                        stringResource(
                            if (uiState.lockMethod == SecurityPrefs.METHOD_PIN) {
                                R.string.privacy_lock_change_pin
                            } else {
                                R.string.privacy_lock_set_pin
                            }
                        )
                    )
                }
                OutlinedButton(
                    onClick = { onEvent(PrivacyViewModel.PrivacyEvent.SetLockPattern) },
                    shape = MoshiShapePill
                ) {
                    Text(
                        stringResource(
                            if (uiState.lockMethod == SecurityPrefs.METHOD_PATTERN) {
                                R.string.privacy_lock_change_pattern
                            } else {
                                R.string.privacy_lock_set_pattern
                            }
                        )
                    )
                }
            }
            if (uiState.lockConfigured) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { onEvent(PrivacyViewModel.PrivacyEvent.DisableLock) }) {
                    Text(stringResource(R.string.privacy_lock_disable))
                }
            }
        }
    }
}

@Composable
private fun PrivacySwitchCard(
    titleRes: Int,
    descRes: Int,
    stateRes: Int?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                if (stateRes != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(stateRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun PrivacyPageScreenPreview() {
    MoshiTheme {
        PrivacyPageScreen(
            uiState = PrivacyViewModel.PrivacyUiState(
                encryptionEnabled = true,
                forceLocal = true,
                storageEncrypted = true,
                lockMethod = SecurityPrefs.METHOD_PIN,
                biometricEnabled = true,
                biometricAvailable = true,
                autoLockEnabled = true
            ),
            onBack = {},
            onEvent = {}
        )
    }
}
