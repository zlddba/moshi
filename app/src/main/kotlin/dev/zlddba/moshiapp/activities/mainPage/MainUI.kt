package dev.zlddba.moshiapp.activities.mainPage

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.cloudPage.CloudActivity
import dev.zlddba.moshiapp.activities.detailPage.DetailActivity
import dev.zlddba.moshiapp.activities.licensePage.LicenseActivity
import dev.zlddba.moshiapp.activities.ocrPage.OcrActivity
import dev.zlddba.moshiapp.activities.privacyPage.ModelActivity
import dev.zlddba.moshiapp.activities.privacyPage.PrivacyActivity
import dev.zlddba.moshiapp.activities.privacyPage.StorageActivity
import dev.zlddba.moshiapp.activities.searchPage.SearchActivity
import dev.zlddba.moshiapp.activities.textPage.TextActivity
import dev.zlddba.moshiapp.activities.voicePage.VoiceActivity
import dev.zlddba.moshiapp.data.repo.IngestRepository
import dev.zlddba.moshiapp.ingest.parse.IngestException
import dev.zlddba.moshiapp.ui.IngestMessages
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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

private fun uriDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        }

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun MainPageScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isCloudEngine by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val chatViewModel: ChatViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(context) as T
            }
        }
    )
    val chatUiState by chatViewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        chatViewModel.onEvent(ChatViewModel.ChatEvent.Init)
        chatViewModel.effects.collect { effect ->
            when (effect) {
                is ChatViewModel.ChatEffect.OpenDetail -> context.startActivity(
                    DetailActivity.createIntent(
                        context = context,
                        noteId = effect.noteId,
                        chunkId = effect.chunkId,
                        keyword = effect.keyword
                    )
                )

                ChatViewModel.ChatEffect.OpenModelPage -> ModelActivity.start(context)

                is ChatViewModel.ChatEffect.ShowToast -> Toast.makeText(
                    context,
                    effect.messageRes,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val showComingSoon = {
        Toast.makeText(context, R.string.capture_coming_soon, Toast.LENGTH_SHORT).show()
    }
    var importStage by remember { mutableStateOf<IngestRepository.Stage?>(null) }
    val importScope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && importStage == null) {
            val name = uriDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty()
            val mime = context.contentResolver.getType(uri)
            importScope.launch {
                try {
                    val summary = IngestRepository.importFile(context, uri, name, mime) { stage ->
                        importStage = stage
                    }
                    importStage = null
                    Toast.makeText(
                        context,
                        context.getString(R.string.capture_file_success, name, summary.chunkCount),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: CancellationException) {
                    importStage = null
                    throw e
                } catch (e: IngestException) {
                    importStage = null
                    Toast.makeText(context, IngestMessages.errorOf(e.kind), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    importStage = null
                    Toast.makeText(context, R.string.ingest_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var ocrSourceDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf("") }
    val ocrPageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ocrSourceDialog = true
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri.isNotEmpty()) {
            ocrPageLauncher.launch(OcrActivity.createIntent(context, pendingCameraUri))
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            ocrPageLauncher.launch(OcrActivity.createIntent(context, uri.toString()))
        }
    }
    val launchCamera = {
        ocrSourceDialog = false
        try {
            val dir = File(context.cacheDir, "ocr").apply { mkdirs() }
            val file = File.createTempFile("shot_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraUri = uri.toString()
            cameraLauncher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.capture_ocr_no_camera, Toast.LENGTH_SHORT).show()
        }
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
            val stage = importStage
            if (stage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (stage is IngestRepository.Stage.Ocring) {
                            stringResource(R.string.capture_import_ocring, stage.page, stage.total)
                        } else {
                            stringResource(stageLabelRes(stage))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            when (selectedTab) {
                0 -> MainHomeScreen(
                    onNoteClick = { DetailActivity.start(context) },
                    onSearchSubmit = { query -> SearchActivity.start(context, query) },
                    isCloudEngine = isCloudEngine
                )

                1 -> MainChatScreen(
                    uiState = chatUiState,
                    onEvent = chatViewModel::onEvent,
                    onEngineClick = { CloudActivity.start(context) },
                    onSettingsClick = { selectedTab = 3 }
                )

                2 -> MainCaptureScreen(
                    onTextClick = { TextActivity.start(context) },
                    onFileClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    onOcrClick = { ocrSourceDialog = true },
                    onVoiceClick = { VoiceActivity.start(context) }
                )

                else -> MainSettingsScreen(
                    onCloudConfig = { CloudActivity.start(context) },
                    onModelManage = { ModelActivity.start(context) },
                    onPrivacy = { PrivacyActivity.start(context) },
                    onStorage = { StorageActivity.start(context) },
                    onLicense = { LicenseActivity.start(context) },
                    onPlaceholder = showComingSoon
                )
            }
        }
    }

    if (ocrSourceDialog) {
        AlertDialog(
            onDismissRequest = { ocrSourceDialog = false },
            title = { Text(text = stringResource(R.string.capture_ocr_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OcrSourceOption(
                        icon = Icons.Outlined.CameraAlt,
                        labelRes = R.string.capture_ocr_camera,
                        onClick = launchCamera
                    )
                    OcrSourceOption(
                        icon = Icons.Outlined.PhotoLibrary,
                        labelRes = R.string.capture_ocr_gallery,
                        onClick = {
                            ocrSourceDialog = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { ocrSourceDialog = false }) {
                    Text(stringResource(R.string.capture_ocr_cancel))
                }
            }
        )
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
private fun OcrSourceOption(
    icon: ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun stageLabelRes(stage: IngestRepository.Stage): Int {
    return when (stage) {
        IngestRepository.Stage.Parsing -> R.string.capture_import_parsing
        IngestRepository.Stage.Chunking -> R.string.capture_import_chunking
        IngestRepository.Stage.Writing -> R.string.capture_import_writing
        is IngestRepository.Stage.Ocring -> R.string.capture_import_ocring
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun MainPageScreenPreview() {
    MoshiTheme {
        MainPageScreen()
    }
}
