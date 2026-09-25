package dev.zlddba.moshiapp.activities.privacyPage

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

private data class PrivacySwitchItem(
    val titleRes: Int,
    val descRes: Int,
    val defaultChecked: Boolean
)

private val privacyItems = listOf(
    PrivacySwitchItem(
        titleRes = R.string.privacy_force_local,
        descRes = R.string.privacy_force_local_desc,
        defaultChecked = true
    ),
    PrivacySwitchItem(
        titleRes = R.string.privacy_encrypt,
        descRes = R.string.privacy_encrypt_desc,
        defaultChecked = false
    ),
    PrivacySwitchItem(
        titleRes = R.string.privacy_autolock,
        descRes = R.string.privacy_autolock_desc,
        defaultChecked = true
    ),
    PrivacySwitchItem(
        titleRes = R.string.privacy_log,
        descRes = R.string.privacy_log_desc,
        defaultChecked = true
    )
)

@Composable
fun PrivacyPageScreen(
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
            titleRes = R.string.privacy_title,
            backDescRes = R.string.privacy_back,
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(8.dp))
        privacyItems.forEach { item ->
            PrivacySwitchCard(item = item)
            Spacer(modifier = Modifier.height(12.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PrivacySwitchCard(item: PrivacySwitchItem) {
    var checked by rememberSaveable { mutableStateOf(item.defaultChecked) }

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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(item.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = { checked = it })
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun PrivacyPageScreenPreview() {
    MoshiTheme {
        PrivacyPageScreen(onBack = {})
    }
}
