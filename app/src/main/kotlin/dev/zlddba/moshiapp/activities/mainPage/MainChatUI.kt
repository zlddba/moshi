package dev.zlddba.moshiapp.activities.mainPage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
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
import dev.zlddba.moshiapp.ui.theme.MoshiShapeLarge
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiShapeSmall
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import kotlinx.coroutines.flow.distinctUntilChanged

data class ChatUiState(
    val isCloudEngine: Boolean = false,
    val messages: List<ChatMessage> = emptyList()
) {
    enum class Role { USER, ASSISTANT, REFUSAL }

    data class ChatMessage(
        val role: Role,
        val text: String = "",
        val sources: List<Int> = emptyList()
    )
}

@Composable
fun sampleChatUiState(): ChatUiState = ChatUiState(
    messages = listOf(
        ChatUiState.ChatMessage(
            role = ChatUiState.Role.USER,
            text = stringResource(R.string.chat_question_demo)
        ),
        ChatUiState.ChatMessage(
            role = ChatUiState.Role.ASSISTANT,
            text = stringResource(R.string.chat_answer_demo),
            sources = listOf(
                R.string.chat_source_note_1,
                R.string.chat_source_note_2
            )
        ),
        ChatUiState.ChatMessage(
            role = ChatUiState.Role.USER,
            text = stringResource(R.string.chat_question_demo_3)
        ),
        ChatUiState.ChatMessage(
            role = ChatUiState.Role.ASSISTANT,
            text = stringResource(R.string.chat_answer_demo_3),
            sources = listOf(R.string.chat_source_note_3)
        ),
        ChatUiState.ChatMessage(
            role = ChatUiState.Role.USER,
            text = stringResource(R.string.chat_question_demo_2)
        ),
        ChatUiState.ChatMessage(
            role = ChatUiState.Role.REFUSAL,
            text = stringResource(R.string.chat_refusal)
        ),
        ChatUiState.ChatMessage(
            role = ChatUiState.Role.USER,
            text = stringResource(R.string.chat_question_demo_4)
        ),
        ChatUiState.ChatMessage(
            role = ChatUiState.Role.ASSISTANT,
            text = stringResource(R.string.chat_answer_demo_4),
            sources = listOf(
                R.string.chat_source_note_2,
                R.string.chat_source_note_4
            )
        )
    )
)

@Composable
fun MainChatScreen(
    onSourceClick: () -> Unit,
    onEngineClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState = sampleChatUiState()
    var input by rememberSaveable { mutableStateOf("") }
    var sessionCleared by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val messages = if (sessionCleared) emptyList() else uiState.messages
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (messages.size - 1).coerceAtLeast(0)
    )
    val imeBottomPx = rememberImeBottomPx()
    val latestMessageIndex = remember { mutableStateOf(-1) }

    SideEffect {
        latestMessageIndex.value = messages.lastIndex
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
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
                        sessionCleared = true
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
            onEngineClick = onEngineClick,
            onSettingsClick = onSettingsClick,
            onClearClick = { showClearDialog = true }
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item { ChatWelcome() }
            }
            items(messages) { message ->
                when (message.role) {
                    ChatUiState.Role.USER -> UserMessage(text = message.text)
                    ChatUiState.Role.ASSISTANT -> AssistantMessage(
                        text = message.text,
                        sources = message.sources,
                        onSourceClick = onSourceClick
                    )

                    ChatUiState.Role.REFUSAL -> RefusalMessage(text = message.text)
                }
            }
        }
        ChatInputBar(
            value = input,
            onValueChange = { input = it },
            onSendClick = { input = "" }
        )
    }
}

@Composable
private fun ChatTopBar(
    isCloudEngine: Boolean,
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
    sources: List<Int>,
    onSourceClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = MoshiShapeMedium.copy(topStart = CornerSize(4.dp)),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                lineHeight = 22.sp
            )
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
                sources.forEach { sourceRes ->
                    Surface(
                        onClick = onSourceClick,
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
                                text = stringResource(sourceRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
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
    onSendClick: () -> Unit
) {
    val canSend = value.isNotBlank()
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
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                maxLines = 4,
                shape = MoshiShapeLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSendClick() })
            )
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = stringResource(R.string.chat_voice_input)
                )
            }
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

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun MainChatScreenPreview() {
    MoshiTheme {
        MainChatScreen(onSourceClick = {}, onEngineClick = {}, onSettingsClick = {})
    }
}
