package dev.zlddba.moshiapp.activities.mainPage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.engine.local.BackendKind
import dev.zlddba.moshiapp.ui.theme.MoshiShapeLarge
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiShapeSmall
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
private fun sampleChatUiState(): ChatViewModel.ChatUiState = ChatViewModel.ChatUiState(
    messages = listOf(
        ChatViewModel.ChatUiState.ChatMessage(
            role = ChatViewModel.ChatUiState.Role.USER,
            text = stringResource(R.string.chat_question_demo)
        ),
        ChatViewModel.ChatUiState.ChatMessage(
            role = ChatViewModel.ChatUiState.Role.ASSISTANT,
            text = stringResource(R.string.chat_answer_demo),
            sources = listOf(
                ChatViewModel.ChatUiState.ChatSource(
                    chunkId = 1,
                    noteId = "note-1",
                    title = stringResource(R.string.chat_source_note_1),
                    pageNo = 3
                ),
                ChatViewModel.ChatUiState.ChatSource(
                    chunkId = 2,
                    noteId = "note-2",
                    title = stringResource(R.string.chat_source_note_2),
                    pageNo = null
                )
            )
        ),
        ChatViewModel.ChatUiState.ChatMessage(
            role = ChatViewModel.ChatUiState.Role.USER,
            text = stringResource(R.string.chat_question_demo_3)
        ),
        ChatViewModel.ChatUiState.ChatMessage(
            role = ChatViewModel.ChatUiState.Role.ASSISTANT,
            text = stringResource(R.string.chat_answer_demo_3),
            sources = listOf(
                ChatViewModel.ChatUiState.ChatSource(
                    chunkId = 3,
                    noteId = "note-3",
                    title = stringResource(R.string.chat_source_note_3),
                    pageNo = 2
                )
            )
        ),
        ChatViewModel.ChatUiState.ChatMessage(
            role = ChatViewModel.ChatUiState.Role.USER,
            text = stringResource(R.string.chat_question_demo_2)
        ),
        ChatViewModel.ChatUiState.ChatMessage(
            role = ChatViewModel.ChatUiState.Role.REFUSAL,
            text = stringResource(R.string.chat_refusal)
        ),
        ChatViewModel.ChatUiState.ChatMessage(
            role = ChatViewModel.ChatUiState.Role.USER,
            text = stringResource(R.string.chat_question_demo_4)
        ),
        ChatViewModel.ChatUiState.ChatMessage(
            role = ChatViewModel.ChatUiState.Role.ASSISTANT,
            text = stringResource(R.string.chat_answer_demo_4),
            sources = listOf(
                ChatViewModel.ChatUiState.ChatSource(
                    chunkId = 4,
                    noteId = "note-2",
                    title = stringResource(R.string.chat_source_note_2),
                    pageNo = null
                ),
                ChatViewModel.ChatUiState.ChatSource(
                    chunkId = 5,
                    noteId = "note-4",
                    title = stringResource(R.string.chat_source_note_4),
                    pageNo = 5
                )
            )
        )
    )
)

@Composable
fun MainChatScreen(
    uiState: ChatViewModel.ChatUiState,
    onEvent: (ChatViewModel.ChatEvent) -> Unit,
    onEngineClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }
    val displayMessages = if (uiState.isGenerating) {
        uiState.messages + ChatViewModel.ChatUiState.ChatMessage(
            role = ChatViewModel.ChatUiState.Role.ASSISTANT,
            text = uiState.streamingText.ifEmpty {
                stringResource(R.string.chat_generating)
            },
            thinking = uiState.streamingThinking,
            autoExpandThinking = uiState.streamingText.isEmpty()
        )
    } else {
        uiState.messages
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (displayMessages.size - 1).coerceAtLeast(0)
    )
    val imeBottomPx = rememberImeBottomPx()
    val latestMessageIndex = remember { mutableStateOf(-1) }

    SideEffect {
        latestMessageIndex.value = displayMessages.lastIndex
    }

    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.lastIndex)
        }
    }

    LaunchedEffect(imeBottomPx) {
        snapshotFlow { imeBottomPx.value > 0 }
            .distinctUntilChanged()
            .collect { imeShown ->
                if (imeShown) {
                    var last = imeBottomPx.value
                    var stableFrames = 0
                    var frames = 0
                    while (stableFrames < 3 && frames < 180) {
                        withFrameNanos { }
                        frames++
                        val current = imeBottomPx.value
                        if (current == last) {
                            stableFrames++
                        } else {
                            stableFrames = 0
                            last = current
                        }
                    }
                    val index = latestMessageIndex.value
                    if (imeBottomPx.value > 0 && index >= 0) {
                        listState.animateScrollToItem(index)
                    }
                }
            }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.chat_clear)) },
            text = { Text(stringResource(R.string.chat_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onEvent(ChatViewModel.ChatEvent.Clear)
                    }
                ) {
                    Text(stringResource(R.string.chat_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.chat_cancel))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        ChatTopBar(
            isCloudEngine = uiState.isCloudEngine,
            backend = uiState.backend,
            onBackendSelected = { onEvent(ChatViewModel.ChatEvent.BackendSelected(it)) },
            onEngineClick = onEngineClick,
            onSettingsClick = onSettingsClick,
            onClearClick = { showClearDialog = true }
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (displayMessages.isEmpty()) {
                item { ChatWelcome() }
            }
            items(displayMessages) { message ->
                when (message.role) {
                    ChatViewModel.ChatUiState.Role.USER -> UserMessage(text = message.text)
                    ChatViewModel.ChatUiState.Role.ASSISTANT -> AssistantMessage(
                        text = message.text,
                        thinking = message.thinking,
                        autoExpandThinking = message.autoExpandThinking,
                        sources = message.sources,
                        onSourceClick = { onEvent(ChatViewModel.ChatEvent.SourceClick(it)) }
                    )

                    ChatViewModel.ChatUiState.Role.REFUSAL -> RefusalMessage(text = message.text)
                }
            }
        }
        if (uiState.modelMissing) {
            ModelMissingCard(onDownloadClick = { onEvent(ChatViewModel.ChatEvent.DownloadModel) })
        }
        ChatInputBar(
            value = uiState.draft,
            onValueChange = { onEvent(ChatViewModel.ChatEvent.DraftChanged(it)) },
            isGenerating = uiState.isGenerating,
            voicePhase = uiState.voicePhase,
            onSendClick = { onEvent(ChatViewModel.ChatEvent.Send(uiState.draft)) },
            onStopClick = { onEvent(ChatViewModel.ChatEvent.Stop) },
            onMicClick = { onEvent(ChatViewModel.ChatEvent.VoiceInput) }
        )
    }
}

@Composable
private fun ChatTopBar(
    isCloudEngine: Boolean,
    backend: String,
    onBackendSelected: (BackendKind) -> Unit,
    onEngineClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.chat_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackendPill(
                backend = backend,
                onSelected = onBackendSelected
            )
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.chat_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onClearClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = stringResource(R.string.chat_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ChatEngineBadge(
                isCloudEngine = isCloudEngine,
                onClick = onEngineClick
            )
        }
    }
}

@Composable
private fun BackendPill(
    backend: String,
    onSelected: (BackendKind) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val kind = BackendKind.from(backend)
    val options = remember { BackendKind.available() }
    val labelRes = when (kind) {
        BackendKind.CPU -> R.string.chat_backend_cpu
        BackendKind.GPU -> R.string.chat_backend_gpu
        BackendKind.NPU -> R.string.chat_backend_npu
    }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = MoshiShapePill,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = stringResource(R.string.chat_backend_menu),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(
                                when (option) {
                                    BackendKind.CPU -> R.string.chat_backend_cpu
                                    BackendKind.GPU -> R.string.chat_backend_gpu
                                    BackendKind.NPU -> R.string.chat_backend_npu
                                }
                            )
                        )
                    },
                    trailingIcon = {
                        if (option == kind) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatEngineBadge(
    isCloudEngine: Boolean,
    onClick: () -> Unit
) {
    val container = if (isCloudEngine) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.primaryContainer
    val onContainer = if (isCloudEngine) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onPrimaryContainer
    val icon: ImageVector = if (isCloudEngine) Icons.Outlined.CloudSync
    else Icons.Outlined.Shield
    val label = if (isCloudEngine) R.string.chat_engine_cloud
    else R.string.chat_engine_local

    Surface(onClick = onClick, shape = MoshiShapePill, color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = onContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                color = onContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ModelMissingCard(onDownloadClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.chat_model_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            TextButton(onClick = onDownloadClick) {
                Text(stringResource(R.string.chat_model_download))
            }
        }
    }
}

@Composable
private fun ChatWelcome() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.chat_welcome),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UserMessage(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = MoshiShapeMedium.copy(topEnd = CornerSize(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun AssistantMessage(
    text: String,
    thinking: String,
    autoExpandThinking: Boolean,
    sources: List<ChatViewModel.ChatUiState.ChatSource>,
    onSourceClick: (ChatViewModel.ChatUiState.ChatSource) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = MoshiShapeMedium.copy(topStart = CornerSize(4.dp)),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (thinking.isNotBlank()) {
                    var thinkingExpanded by rememberSaveable { mutableStateOf(autoExpandThinking) }
                    LaunchedEffect(autoExpandThinking) {
                        thinkingExpanded = autoExpandThinking
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { thinkingExpanded = !thinkingExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.chat_thinking),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (thinkingExpanded) {
                                Icons.Outlined.KeyboardArrowUp
                            } else {
                                Icons.Outlined.KeyboardArrowDown
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (thinkingExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = thinking,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            }
        }
        if (sources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.chat_source_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sources.forEach { source ->
                    Surface(
                        onClick = { onSourceClick(source) },
                        shape = MoshiShapeSmall,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 6.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = source.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            source.pageNo?.let { page ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.detail_page, page),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RefusalMessage(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            shape = MoshiShapeSmall,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun rememberImeBottomPx(): State<Int> {
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    return remember(density, ime) {
        derivedStateOf { ime.getBottom(density) }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isGenerating: Boolean,
    voicePhase: ChatViewModel.ChatUiState.VoiceInputPhase,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onMicClick: () -> Unit
) {
    val canSend = value.isNotBlank() && !isGenerating
    val placeholderText = when (voicePhase) {
        ChatViewModel.ChatUiState.VoiceInputPhase.RECORDING ->
            stringResource(R.string.chat_voice_recording_hint)

        ChatViewModel.ChatUiState.VoiceInputPhase.IDLE ->
            stringResource(R.string.chat_input_hint)
    }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholderText) },
                maxLines = 4,
                shape = MoshiShapeLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSendClick() })
            )
            if (voicePhase == ChatViewModel.ChatUiState.VoiceInputPhase.RECORDING) {
                IconButton(onClick = onMicClick) {
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = stringResource(R.string.voice_stop_record),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                IconButton(
                    onClick = onMicClick,
                    enabled = !isGenerating &&
                        voicePhase == ChatViewModel.ChatUiState.VoiceInputPhase.IDLE
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = stringResource(R.string.chat_voice_input)
                    )
                }
            }
            if (isGenerating) {
                Surface(
                    onClick = onStopClick,
                    shape = MoshiShapePill,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = stringResource(R.string.chat_stop),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                Surface(
                    onClick = { if (canSend) onSendClick() },
                    enabled = canSend,
                    shape = MoshiShapePill,
                    color = if (canSend) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (canSend) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun MainChatScreenPreview() {
    MoshiTheme {
        MainChatScreen(
            uiState = sampleChatUiState(),
            onEvent = {},
            onEngineClick = {},
            onSettingsClick = {}
        )
    }
}
