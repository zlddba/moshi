package dev.zlddba.moshiapp.activities.policyPage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun PolicyPageScreen(
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
            titleRes = R.string.policy_title,
            backDescRes = R.string.policy_back,
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.policy_updated),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.policy_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))
        PolicyGroupTitle(titleRes = R.string.policy_group_storage)
        PolicyCard(bodyRes = R.string.policy_storage_body)

        Spacer(modifier = Modifier.height(16.dp))
        PolicyGroupTitle(titleRes = R.string.policy_group_permission)
        PolicyCard(bodyRes = R.string.policy_permission_body)

        Spacer(modifier = Modifier.height(16.dp))
        PolicyGroupTitle(titleRes = R.string.policy_group_cloud)
        PolicyCard(bodyRes = R.string.policy_cloud_body)

        Spacer(modifier = Modifier.height(16.dp))
        PolicyGroupTitle(titleRes = R.string.policy_group_third)
        PolicyCard(bodyRes = R.string.policy_third_body)

        Spacer(modifier = Modifier.height(16.dp))
        PolicyGroupTitle(titleRes = R.string.policy_group_rights)
        PolicyCard(bodyRes = R.string.policy_rights_body)

        Spacer(modifier = Modifier.height(16.dp))
        PolicyGroupTitle(titleRes = R.string.policy_group_update)
        PolicyCard(bodyRes = R.string.policy_update_body)

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.policy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PolicyGroupTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun PolicyCard(bodyRes: Int) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            lineHeight = 18.sp
        )
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun PolicyPageScreenPreview() {
    MoshiTheme {
        PolicyPageScreen(onBack = {})
    }
}
