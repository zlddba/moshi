package dev.zlddba.moshiapp.activities.mainPage

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.cloudPage.CloudActivity
import dev.zlddba.moshiapp.activities.detailPage.DetailActivity
import dev.zlddba.moshiapp.activities.ocrPage.OcrActivity
import dev.zlddba.moshiapp.activities.privacyPage.ModelActivity
import dev.zlddba.moshiapp.activities.privacyPage.PrivacyActivity
import dev.zlddba.moshiapp.activities.privacyPage.StorageActivity
import dev.zlddba.moshiapp.activities.voicePage.VoiceActivity
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

private data class MainTabItem(
    val icon: ImageVector,
    val labelRes: Int
)

private val mainTabItems = listOf(
    MainTabItem(icon = Icons.Outlined.Home, labelRes = R.string.main_tab_home),
    MainTabItem(icon = Icons.AutoMirrored.Outlined.Chat, labelRes = R.string.main_tab_chat),
    MainTabItem(icon = Icons.Outlined.Add, labelRes = R.string.main_tab_capture),
    MainTabItem(icon = Icons.Outlined.Person, labelRes = R.string.main_tab_mine)
)

@Composable
fun MainPageScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isCloudEngine by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val showComingSoon = {
        Toast.makeText(context, R.string.capture_coming_soon, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            MainBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> MainHomeScreen(
                    onNoteClick = { DetailActivity.start(context) },
                    isCloudEngine = isCloudEngine
                )

                1 -> MainChatScreen(
                    onSourceClick = { DetailActivity.start(context) },
                    onEngineClick = { CloudActivity.start(context) },
                    onSettingsClick = { selectedTab = 3 }
                )

                2 -> MainCaptureScreen(
                    onTextClick = showComingSoon,
                    onFileClick = showComingSoon,
                    onOcrClick = { OcrActivity.start(context) },
                    onVoiceClick = { VoiceActivity.start(context) }
                )

                else -> MainSettingsScreen(
                    onCloudConfig = { CloudActivity.start(context) },
                    onModelManage = { ModelActivity.start(context) },
                    onPrivacy = { PrivacyActivity.start(context) },
                    onStorage = { StorageActivity.start(context) },
                    onPlaceholder = showComingSoon
                )
            }
        }
    }
}

@Composable
private fun MainBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            mainTabItems.forEachIndexed { index, item ->
                MainTabButton(
                    item = item,
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MainTabButton(
    item: MainTabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(onClick = onClick, modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(30.dp)
                    .clip(MoshiShapePill)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(item.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun MainPageScreenPreview() {
    MoshiTheme {
        MainPageScreen()
    }
}
