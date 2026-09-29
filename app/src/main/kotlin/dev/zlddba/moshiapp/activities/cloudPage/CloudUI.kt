package dev.zlddba.moshiapp.activities.cloudPage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.engine.cloud.CloudConfig
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

@Composable
fun CloudPageScreen(
    uiState: CloudViewModel.CloudUiState,
    onEvent: (CloudViewModel.CloudEvent) -> Unit,
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
            titleRes = R.string.cloud_title,
            backDescRes = R.string.cloud_back,
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(8.dp))
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
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cloud_enable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.cloud_enable_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = uiState.mode != CloudConfig.MODE_LOCAL,
                    onCheckedChange = {
                        onEvent(CloudViewModel.CloudEvent.EnabledChanged(it))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        CloudField(
            labelRes = R.string.cloud_base_url,
            hintRes = R.string.cloud_base_url_hint,
            value = uiState.baseUrl,
            onValueChange = { onEvent(CloudViewModel.CloudEvent.BaseUrlChanged(it)) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.apiKey,
            onValueChange = { onEvent(CloudViewModel.CloudEvent.ApiKeyChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.cloud_api_key)) },
            placeholder = { Text(stringResource(R.string.cloud_api_key_hint)) },
            singleLine = true,
            shape = MoshiShapeMedium,
            visualTransformation = if (uiState.keyVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { onEvent(CloudViewModel.CloudEvent.KeyVisibilityToggled) }) {
                    Icon(
                        imageVector = if (uiState.keyVisible) Icons.Outlined.VisibilityOff
                        else Icons.Outlined.Visibility,
                        contentDescription = stringResource(
                            if (uiState.keyVisible) R.string.cloud_api_key_hide
                            else R.string.cloud_api_key_show
                        )
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        CloudField(
            labelRes = R.string.cloud_model_name,
            hintRes = R.string.cloud_model_name_hint,
            value = uiState.modelName,
            onValueChange = { onEvent(CloudViewModel.CloudEvent.ModelNameChanged(it)) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        CloudPresetRow(
            onPresetClick = { res ->
                val url = when (res) {
                    R.string.cloud_preset_deepseek -> "https://api.deepseek.com/v1"
                    R.string.cloud_preset_qwen -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
                    else -> "https://open.bigmodel.cn/api/paas/v4"
                }
                onEvent(CloudViewModel.CloudEvent.PresetClicked(url))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
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
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cloud_force_local),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.cloud_force_local_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = uiState.forceLocal,
                    onCheckedChange = { onEvent(CloudViewModel.CloudEvent.ForceLocalChanged(it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        val testing = uiState.testState == CloudViewModel.TestState.Testing
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onEvent(CloudViewModel.CloudEvent.TestClicked) },
                enabled = !testing,
                shape = MoshiShapeMedium
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.cloud_testing))
                } else {
                    Text(stringResource(R.string.cloud_test))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            when (val testState = uiState.testState) {
                CloudViewModel.TestState.Success -> Text(
                    text = stringResource(R.string.cloud_test_success),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )

                is CloudViewModel.TestState.ValidationFailed -> Text(
                    text = stringResource(testState.resId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )

                is CloudViewModel.TestState.ServerFailed -> {
                    val friendly = stringResource(testState.resId, testState.statusCode)
                    val message = if (testState.detail.isBlank()) friendly
                    else "$friendly（${testState.detail}）"
                    Text(
                        text = stringResource(R.string.cloud_test_fail, message),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                else -> Unit
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            shape = MoshiShapeMedium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.cloud_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(14.dp),
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onEvent(CloudViewModel.CloudEvent.SaveClicked) },
            modifier = Modifier.fillMaxWidth(),
            shape = MoshiShapeMedium
        ) {
            Text(stringResource(R.string.cloud_save))
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CloudField(
    labelRes: Int,
    hintRes: Int,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(labelRes)) },
        placeholder = { Text(stringResource(hintRes)) },
        singleLine = true,
        shape = MoshiShapeMedium
    )
}

@Composable
private fun CloudPresetRow(onPresetClick: (Int) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.cloud_preset_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                R.string.cloud_preset_deepseek,
                R.string.cloud_preset_qwen,
                R.string.cloud_preset_zhipu
            ).forEach { res ->
                Surface(
                    onClick = { onPresetClick(res) },
                    shape = MoshiShapePill,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun CloudPageScreenPreview() {
    MoshiTheme {
        CloudPageScreen(
            uiState = CloudViewModel.CloudUiState(
                baseUrl = "https://api.deepseek.com/v1",
                apiKey = "sk-demo",
                modelName = "deepseek-chat"
            ),
            onEvent = {},
            onBack = {}
        )
    }
}
