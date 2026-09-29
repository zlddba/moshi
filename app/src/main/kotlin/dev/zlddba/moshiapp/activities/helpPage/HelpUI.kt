package dev.zlddba.moshiapp.activities.helpPage

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
import androidx.compose.runtime.remember
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

private sealed interface HelpBlock {

    data class Section(val title: String) : HelpBlock

    data class Paragraph(val text: String) : HelpBlock
}

private fun parseManual(raw: String): List<HelpBlock> {
    if (raw.isBlank()) return emptyList()
    val blocks = mutableListOf<HelpBlock>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) {
            blocks.add(HelpBlock.Paragraph(paragraph.toString().trim()))
        }
        paragraph.setLength(0)
    }
    for (line in raw.lines()) {
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> flush()
            trimmed.startsWith("# ") -> flush()
            trimmed.startsWith("## ") -> {
                flush()
                blocks.add(HelpBlock.Section(trimmed.removePrefix("## ")))
            }
            else -> {
                if (paragraph.isNotEmpty()) {
                    paragraph.append('\n')
                }
                if (trimmed.startsWith("- ")) {
                    paragraph.append("• ").append(trimmed.removePrefix("- "))
                } else {
                    paragraph.append(trimmed)
                }
            }
        }
    }
    flush()
    return blocks
}

@Composable
fun HelpPageScreen(
    manual: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(manual) { parseManual(manual) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        PageTopBar(
            titleRes = R.string.help_title,
            backDescRes = R.string.help_back,
            onBack = onBack
        )
        if (blocks.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.help_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
        for (block in blocks) {
            when (block) {
                is HelpBlock.Section -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    HelpGroupTitle(title = block.title)
                }
                is HelpBlock.Paragraph -> {
                    HelpCard(text = block.text)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun HelpGroupTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun HelpCard(text: String) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            lineHeight = 18.sp
        )
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun HelpPageScreenPreview() {
    MoshiTheme {
        HelpPageScreen(
            manual = "# 默识使用说明\n\n默识是你的本地知识问答助手。\n\n## 快速上手\n\n1. 在「采集」页导入知识。\n2. 到「问答」页提问。\n- 支持文件导入\n- 支持截图 OCR",
            onBack = {}
        )
    }
}
