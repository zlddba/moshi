package dev.zlddba.moshiapp.activities.licensePage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

private data class LicenseDep(
    val name: String,
    val version: String,
    val license: String
)

private val runtimeDeps = listOf(
    LicenseDep("Jetpack Compose (BOM)", "2026.09.00", "Apache-2.0"),
    LicenseDep("Compose Material Icons", "2026.09.00", "Apache-2.0"),
    LicenseDep("AndroidX Activity Compose", "1.13.0", "Apache-2.0"),
    LicenseDep("AndroidX Core KTX", "1.19.0", "Apache-2.0"),
    LicenseDep("AndroidX Lifecycle", "2.11.0", "Apache-2.0"),
    LicenseDep("kotlinx-serialization-json", "1.11.0", "Apache-2.0"),
    LicenseDep("Coil", "3.6.3", "Apache-2.0"),
    LicenseDep("Ktor OkHttp Engine", "3.0.0", "Apache-2.0"),
    LicenseDep("openai-kotlin", "4.1.0", "MIT")
)

private val devDeps = listOf(
    LicenseDep("Kotlin", "2.4.20", "Apache-2.0"),
    LicenseDep("Android Gradle Plugin", "9.4.1", "Apache-2.0"),
    LicenseDep("JUnit Jupiter", "6.1.3", "EPL-2.0"),
    LicenseDep("AndroidX Test / Espresso", "1.3.0 / 3.7.0", "Apache-2.0")
)

@Composable
fun LicensePageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var apacheExpanded by rememberSaveable { mutableStateOf(false) }
    var mitExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        PageTopBar(
            titleRes = R.string.license_title,
            backDescRes = R.string.license_back,
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.license_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))
        LicenseGroupTitle(titleRes = R.string.license_group_app)
        LicenseCard {
            Text(
                text = stringResource(R.string.app_name_zh),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)
            )
            Text(
                text = stringResource(R.string.license_app_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        LicenseGroupTitle(titleRes = R.string.license_group_runtime)
        LicenseCard {
            runtimeDeps.forEach { dep -> LicenseDepRow(dep = dep) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        LicenseGroupTitle(titleRes = R.string.license_group_dev)
        LicenseCard {
            devDeps.forEach { dep -> LicenseDepRow(dep = dep) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.license_note_transitive),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))
        LicenseGroupTitle(titleRes = R.string.license_group_full)
        LicenseExpandableCard(
            title = stringResource(R.string.license_full_apache),
            expanded = apacheExpanded,
            onToggle = { apacheExpanded = !apacheExpanded },
            text = stringResource(R.string.license_apache_text)
        )
        Spacer(modifier = Modifier.height(12.dp))
        LicenseExpandableCard(
            title = stringResource(R.string.license_full_mit),
            expanded = mitExpanded,
            onToggle = { mitExpanded = !mitExpanded },
            text = stringResource(R.string.license_mit_text)
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun LicenseGroupTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun LicenseCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun LicenseDepRow(dep: LicenseDep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dep.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = dep.version,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = dep.license,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LicenseExpandableCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    text: String
) {
    Surface(
        onClick = onToggle,
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(
                        if (expanded) R.string.license_collapse else R.string.license_expand
                    ),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (expanded) 90f else 0f),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            if (expanded) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun LicensePageScreenPreview() {
    MoshiTheme {
        LicensePageScreen(onBack = {})
    }
}
