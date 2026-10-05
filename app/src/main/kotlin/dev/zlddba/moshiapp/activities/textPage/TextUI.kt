package dev.zlddba.moshiapp.activities.textPage

import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.ui.doc.HtmlRenderer
import dev.zlddba.moshiapp.ui.doc.HtmlView
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

@Composable
fun TextPageScreen(
    uiState: TextViewModel.TextUiState,
    onBack: () -> Unit,
    onEvent: (TextViewModel.TextEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val onPasteClick = {
        val text = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        if (!text.isNullOrEmpty()) {
            onEvent(TextViewModel.TextEvent.PasteText(text))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        PageTopBar(
            titleRes = R.string.text_title,
            backDescRes = R.string.text_title,
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(8.dp))
        TitleField(
            title = uiState.title,
            generating = uiState.isGeneratingTitle,
            onTitleChange = { onEvent(TextViewModel.TextEvent.TitleChanged(it)) },
            onRegenerate = { onEvent(TextViewModel.TextEvent.RegenerateTitle) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.tags,
            onValueChange = { onEvent(TextViewModel.TextEvent.TagsChanged(it)) },
            singleLine = true,
            label = { Text(stringResource(R.string.tag_label)) },
            placeholder = { Text(stringResource(R.string.tag_input_hint)) },
            shape = MoshiShapeMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextModeTabs(
                isPreview = uiState.isPreview,
                onModeChange = { onEvent(TextViewModel.TextEvent.ModeChanged(it)) }
            )
            Spacer(modifier = Modifier.weight(1f))
            PasteButton(onClick = onPasteClick)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.text_char_count, uiState.content.length),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.align(Alignment.End)
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (uiState.isPreview) {
            MarkdownPreviewCard(markdown = uiState.content, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(
                value = uiState.content,
                onValueChange = { onEvent(TextViewModel.TextEvent.ContentChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.text_input_hint)) },
                minLines = 12,
                maxLines = 16,
                shape = MoshiShapeMedium
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onEvent(TextViewModel.TextEvent.ConfirmClicked) },
            modifier = Modifier.fillMaxWidth(),
            shape = MoshiShapeMedium
        ) {
            Text(stringResource(R.string.text_confirm))
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun TitleField(
    title: String,
    generating: Boolean,
    onTitleChange: (String) -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            singleLine = true,
            label = { Text(stringResource(R.string.ingest_title_label)) },
            placeholder = { Text(stringResource(R.string.ingest_title_hint)) },
            shape = MoshiShapeMedium,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = onRegenerate,
            enabled = !generating,
            shape = MoshiShapePill,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (generating) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.ingest_title_generate),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun TextModeTabs(
    isPreview: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextModeTab(
            label = stringResource(R.string.text_tab_edit),
            selected = !isPreview,
            onClick = { onModeChange(false) }
        )
        TextModeTab(
            label = stringResource(R.string.text_tab_preview),
            selected = isPreview,
            onClick = { onModeChange(true) }
        )
    }
}

@Composable
private fun TextModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MoshiShapePill,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PasteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = MoshiShapePill,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Outlined.ContentPaste,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.text_paste),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun MarkdownPreviewCard(markdown: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        if (markdown.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.text_preview_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            val dark = isSystemInDarkTheme()
            val html = androidx.compose.runtime.remember(markdown, dark) {
                HtmlRenderer.page(markdown, dark)
            }
            HtmlView(
                html = html,
                maxHeight = 420,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun TextPageScreenPreview() {
    MoshiTheme {
        TextPageScreen(
            uiState = TextViewModel.TextUiState(
                title = "向量检索笔记",
                content = "# 标题\n\n正文 **加粗** 与 `代码`"
            ),
            onBack = {},
            onEvent = {}
        )
    }
}
