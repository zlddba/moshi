package dev.zlddba.moshiapp.activities.detailPage

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiShapeSmall
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

data class DetailUiState(
    val noteId: String = "",
    val isSensitive: Boolean = false,
    val title: String = "",
    val summary: String = "",
    val heading: String = "",
    val paragraphs: List<Paragraph> = emptyList(),
    val sources: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val related: List<RelatedNote> = emptyList(),
    val highlight: String = "",
    val focusIndex: Int = -1,
    val missing: Boolean = false
) {
    data class Paragraph(
        val text: String,
        val pageNo: Int? = null
    )

    data class RelatedNote(
        val title: String,
        val score: String
    )
}

fun sampleDetailUiState(context: Context): DetailUiState = DetailUiState(
    title = context.getString(R.string.home_card_title_1),
    summary = context.getString(R.string.detail_summary_demo),
    heading = context.getString(R.string.detail_content_heading),
    paragraphs = listOf(
        DetailUiState.Paragraph(text = context.getString(R.string.detail_content_body))
    ),
    sources = listOf(
        context.getString(R.string.detail_source_type),
        context.getString(R.string.detail_source_time),
        context.getString(R.string.detail_source_note)
    ),
    tags = listOf(
        context.getString(R.string.home_card_tag_db),
        context.getString(R.string.home_card_tag_ml)
    ),
    related = listOf(
        DetailUiState.RelatedNote(
            title = context.getString(R.string.detail_related_1),
            score = context.getString(R.string.detail_related_score_1)
        ),
        DetailUiState.RelatedNote(
            title = context.getString(R.string.detail_related_2),
            score = context.getString(R.string.detail_related_score_2)
        )
    )
)

@Composable
fun DetailPageScreen(
    uiState: DetailUiState,
    onBack: () -> Unit,
    onRelatedClick: () -> Unit = {},
    onToggleSensitive: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.focusIndex) {
        if (uiState.focusIndex >= 0) {
            listState.animateScrollToItem(uiState.focusIndex + 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        DetailTopBar(
            onBack = onBack,
            menuExpanded = menuExpanded,
            onMenuToggle = { menuExpanded = it },
            onDeleteClick = {
                menuExpanded = false
                deleteDialogVisible = true
            }
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            item {
                Column {
                    Text(
                        text = uiState.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (uiState.summary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        DetailSummaryCard(summary = uiState.summary)
                    }
                    if (uiState.heading.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.heading,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            itemsIndexed(
                items = uiState.paragraphs,
                key = { index, _ -> "paragraph-$index" }
            ) { _, paragraph ->
                DetailParagraph(
                    text = paragraph.text,
                    pageNo = paragraph.pageNo,
                    highlight = uiState.highlight
                )
            }
            if (uiState.sources.isNotEmpty()) {
                item {
                    DetailSourceCard(rows = uiState.sources)
                }
            }
            if (uiState.noteId.isNotBlank()) {
                item {
                    DetailSensitiveCard(
                        isSensitive = uiState.isSensitive,
                        onToggle = onToggleSensitive
                    )
                }
            }
            if (uiState.tags.isNotEmpty()) {
                item {
                    DetailTagSection(tags = uiState.tags)
                }
            }
            if (uiState.related.isNotEmpty()) {
                item {
                    DetailRelatedSection(
                        related = uiState.related,
                        onRelatedClick = onRelatedClick
                    )
                }
            }
        }
    }

    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text(stringResource(R.string.detail_delete)) },
            text = { Text(stringResource(R.string.detail_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialogVisible = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.detail_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogVisible = false }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            }
        )
    }
}

@Composable
private fun DetailTopBar(
    onBack: () -> Unit,
    menuExpanded: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.detail_back)
            )
        }
        Text(
            text = stringResource(R.string.detail_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Box {
            IconButton(onClick = { onMenuToggle(true) }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.detail_more)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuToggle(false) }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.detail_delete)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null
                        )
                    },
                    onClick = onDeleteClick
                )
            }
        }
    }
}

@Composable
private fun DetailSummaryCard(summary: String) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.detail_summary_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.detail_summary_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun DetailParagraph(
    text: String,
    pageNo: Int?,
    highlight: String
) {
    val markBackground = MaterialTheme.colorScheme.primaryContainer
    val markColor = MaterialTheme.colorScheme.onPrimaryContainer
    val annotated = remember(text, highlight) {
        buildAnnotatedString {
            append(text)
            if (highlight.isNotBlank()) {
                var index = text.indexOf(highlight, ignoreCase = true)
                while (index >= 0) {
                    addStyle(
                        SpanStyle(
                            background = markBackground,
                            color = markColor,
                            fontWeight = FontWeight.Medium
                        ),
                        index,
                        index + highlight.length
                    )
                    index = text.indexOf(
                        highlight,
                        index + highlight.length,
                        ignoreCase = true
                    )
                }
            }
        }
    }

    Column {
        val page = pageNo
        if (page != null) {
            Text(
                text = stringResource(R.string.detail_page, page),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MoshiShapePill)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun DetailSensitiveCard(
    isSensitive: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.detail_sensitive),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.detail_sensitive_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = isSensitive, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun DetailSourceCard(rows: List<String>) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.detail_source_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            rows.forEach { row ->
                Text(
                    text = row,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailTagSection(tags: List<String>) {
    Column {
        Text(
            text = stringResource(R.string.detail_tag_edit),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                Surface(
                    shape = MoshiShapePill,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            Surface(
                onClick = {},
                shape = MoshiShapePill,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Text(
                    text = stringResource(R.string.detail_tag_add),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailRelatedSection(
    related: List<DetailUiState.RelatedNote>,
    onRelatedClick: () -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.detail_related),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            related.forEach { note ->
                Surface(
                    onClick = onRelatedClick,
                    shape = MoshiShapeSmall,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(MoshiShapeSmall)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = note.score,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun DetailPageScreenPreview() {
    MoshiTheme {
        DetailPageScreen(
            uiState = sampleDetailUiState(LocalContext.current),
            onBack = {}
        )
    }
}
