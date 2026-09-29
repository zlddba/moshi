package dev.zlddba.moshiapp.activities.textPage

import android.content.ClipboardManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

private const val MARKDOWN_CSS_LIGHT =
    "body{font-family:sans-serif;font-size:15px;line-height:1.6;padding:16px;margin:0;color:#1C1B1F;background:transparent;}" +
        "h1,h2{border-bottom:1px solid #CAC4D0;padding-bottom:4px;}h1{font-size:1.5em;}h2{font-size:1.3em;}h3{font-size:1.15em;}" +
        "p{margin:8px 0;}code{background:#F3EDF7;padding:1px 5px;border-radius:4px;font-size:13px;}" +
        "pre{background:#F3EDF7;padding:12px;border-radius:8px;overflow-x:auto;}pre code{background:transparent;padding:0;}" +
        "blockquote{border-left:3px solid #79747E;margin:8px 0;padding-left:12px;color:#49454F;}a{color:#6750A4;}" +
        "table{border-collapse:collapse;width:100%;margin:8px 0;}th,td{border:1px solid #CAC4D0;padding:6px 8px;}" +
        "th{background:#F3EDF7;}hr{border:none;border-top:1px solid #CAC4D0;margin:12px 0;}" +
        "ul,ol{padding-left:22px;}img{max-width:100%;}"

private const val MARKDOWN_CSS_DARK =
    "body{font-family:sans-serif;font-size:15px;line-height:1.6;padding:16px;margin:0;color:#E6E0E9;background:transparent;}" +
        "h1,h2{border-bottom:1px solid #49454F;padding-bottom:4px;}h1{font-size:1.5em;}h2{font-size:1.3em;}h3{font-size:1.15em;}" +
        "p{margin:8px 0;}code{background:#2B2930;padding:1px 5px;border-radius:4px;font-size:13px;}" +
        "pre{background:#2B2930;padding:12px;border-radius:8px;overflow-x:auto;}pre code{background:transparent;padding:0;}" +
        "blockquote{border-left:3px solid #938F99;margin:8px 0;padding-left:12px;color:#CAC4D0;}a{color:#D0BCFF;}" +
        "table{border-collapse:collapse;width:100%;margin:8px 0;}th,td{border:1px solid #49454F;padding:6px 8px;}" +
        "th{background:#2B2930;}hr{border:none;border-top:1px solid #49454F;margin:12px 0;}" +
        "ul,ol{padding-left:22px;}img{max-width:100%;}"

private fun renderMarkdownHtml(markdown: String, dark: Boolean): String {
    val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        AutolinkExtension.create()
    )
    val document = Parser.builder().extensions(extensions).build().parse(markdown)
    val body = HtmlRenderer.builder().extensions(extensions).build().render(document)
    val css = if (dark) MARKDOWN_CSS_DARK else MARKDOWN_CSS_LIGHT
    return "<!DOCTYPE html><html><head>" +
        "<meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<style>$css</style></head><body>$body</body></html>"
}

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
            MarkdownPreviewCard(
                markdown = uiState.content,
                modifier = Modifier.fillMaxWidth()
            )
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
        shadowElevation = 2.dp,
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
            val html = remember(markdown, dark) { renderMarkdownHtml(markdown, dark) }
            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        settings.javaScriptEnabled = false
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = true
                        }
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            )
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun TextPageScreenPreview() {
    MoshiTheme {
        TextPageScreen(
            uiState = TextViewModel.TextUiState(content = "# 标题\n\n正文 **加粗** 与 `代码`"),
            onBack = {},
            onEvent = {}
        )
    }
}
