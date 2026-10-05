package dev.zlddba.moshiapp.activities.detailPage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.ui.doc.AudioPlayer
import dev.zlddba.moshiapp.ui.doc.DocumentRenderer
import dev.zlddba.moshiapp.ui.doc.HtmlRenderer
import dev.zlddba.moshiapp.ui.doc.HtmlView
import dev.zlddba.moshiapp.ui.doc.PdfView
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiShapeSmall
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import dev.zlddba.moshiapp.ui.theme.TagChips
import java.io.File

data class DetailUiState(
    val noteId: String = "",
    val isSensitive: Boolean = false,
    val isBuiltIn: Boolean = false,
    val title: String = "",
    val abstract: String = "",
    val bodyDraft: String = "",
    val tagsDraft: String = "",
    val heading: String = "",
    val content: ContentBlock = ContentBlock(),
    val paragraphs: List<Paragraph> = emptyList(),
    val sources: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val related: List<RelatedNote> = emptyList(),
    val highlight: String = "",
    val focusIndex: Int = -1,
    val hitSnippet: String = "",
    val hitRange: IntRange? = null,
    val isEditing: Boolean = false,
    val missing: Boolean = false
) {
    val hasAbstract: Boolean get() = abstract.isNotBlank()

    data class ContentBlock(
        val kind: DocumentRenderer.Kind = DocumentRenderer.Kind.TEXT,
        val text: String = "",
        val file: File? = null,
        val fileLabel: String = ""
    )

    data class Paragraph(
        val text: String,
        val pageNo: Int? = null
    )

    data class RelatedNote(
        val noteId: String = "",
        val title: String,
        val score: String
    )
}

fun sampleDetailUiState(context: Context): DetailUiState = DetailUiState(
    title = context.getString(R.string.home_card_title_1),
    abstract = context.getString(R.string.detail_summary_demo),
    heading = context.getString(R.string.detail_content_heading),
    content = DetailUiState.ContentBlock(text = context.getString(R.string.detail_content_body)),
    sources = listOf(
        context.getString(R.string.detail_source_type),
        context.getString(R.string.detail_source_time)
    ),
    tags = listOf(
        context.getString(R.string.home_card_tag_db),
        context.getString(R.string.home_card_tag_ml)
    ),
    related = listOf(
        DetailUiState.RelatedNote(
            title = context.getString(R.string.detail_related_1),
            score = context.getString(R.string.detail_related_score_1)
        )
    )
)

@Composable
fun DetailPageScreen(
    uiState: DetailUiState,
    onBack: () -> Unit,
    onRelatedClick: (String) -> Unit = {},
    onToggleSensitive: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEvent: (DetailViewModel.DetailEvent) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        DetailTopBar(
            onBack = onBack,
            canEdit = !uiState.isBuiltIn && !uiState.missing,
            isEditing = uiState.isEditing,
            onEditClick = { onEvent(DetailViewModel.DetailEvent.StartEdit) },
            menuExpanded = menuExpanded,
            onMenuToggle = { menuExpanded = it },
            onDeleteClick = if (uiState.isBuiltIn) {
                null
            } else {
                {
                    menuExpanded = false
                    deleteDialogVisible = true
                }
            }
        )
        if (uiState.missing) {
            MissingBody()
        } else if (uiState.isEditing) {
            EditBody(uiState = uiState, onEvent = onEvent)
        } else {
            ViewBody(uiState = uiState, onRelatedClick = onRelatedClick, onEvent = onEvent)
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
private fun ViewBody(
    uiState: DetailUiState,
    onRelatedClick: (String) -> Unit,
    onEvent: (DetailViewModel.DetailEvent) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { NoteHeader(uiState = uiState, onToggleSensitive = onEvent) }
        if (uiState.hitSnippet.isNotBlank()) {
            item { HitSnippetCard(snippet = uiState.hitSnippet, keyword = uiState.highlight) }
        }
        item {
            SummaryCard(
                text = uiState.abstract,
                onRegenerate = { onEvent(DetailViewModel.DetailEvent.RegenerateSummary) }
            )
        }
        item { TagRow(tags = uiState.tags) }
        item { ContentCard(uiState = uiState) }
        if (uiState.related.isNotEmpty()) {
            item { RelatedSection(related = uiState.related, onClick = onRelatedClick) }
        }
    }
}

@Composable
private fun EditBody(
    uiState: DetailUiState,
    onEvent: (DetailViewModel.DetailEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.title,
            onValueChange = { onEvent(DetailViewModel.DetailEvent.TitleChanged(it)) },
            singleLine = true,
            label = { Text(stringResource(R.string.ingest_title_label)) },
            shape = MoshiShapeMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = uiState.tagsDraft,
            onValueChange = { onEvent(DetailViewModel.DetailEvent.TagsChanged(it)) },
            singleLine = true,
            label = { Text(stringResource(R.string.tag_label)) },
            placeholder = { Text(stringResource(R.string.tag_input_hint)) },
            shape = MoshiShapeMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = uiState.bodyDraft,
            onValueChange = { onEvent(DetailViewModel.DetailEvent.BodyChanged(it)) },
            label = { Text(stringResource(R.string.detail_edit_body_label)) },
            minLines = 10,
            maxLines = 20,
            shape = MoshiShapeMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onEvent(DetailViewModel.DetailEvent.CancelEdit) },
                shape = MoshiShapeMedium,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.detail_cancel))
            }
            Button(
                onClick = { onEvent(DetailViewModel.DetailEvent.SaveEdit) },
                shape = MoshiShapeMedium,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.detail_save))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.detail_edit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun NoteHeader(
    uiState: DetailUiState,
    onToggleSensitive: (DetailViewModel.DetailEvent) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = uiState.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                uiState.sources.forEach { source ->
                    Text(
                        text = source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = stringResource(R.string.detail_force_local),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Switch(
                checked = uiState.isSensitive,
                onCheckedChange = { onToggleSensitive(DetailViewModel.DetailEvent.ToggleSensitive) }
            )
        }
    }
}

@Composable
private fun SummaryCard(text: String, onRegenerate: () -> Unit) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.detail_summary_heading),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRegenerate) {
                    Text(
                        text = stringResource(R.string.detail_summary_regenerate),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text.ifBlank { stringResource(R.string.detail_summary_empty) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun TagRow(tags: List<String>) {
    if (tags.isEmpty()) {
        Text(
            text = stringResource(R.string.tag_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        return
    }
    TagChips(tags = tags)
}

@Composable
private fun ContentCard(uiState: DetailUiState) {
    val block = uiState.content
    val dark = isSystemInDarkTheme()
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = uiState.heading,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (block.kind != DocumentRenderer.Kind.TEXT) {
                    Text(
                        text = block.fileLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            when (block.kind) {
                DocumentRenderer.Kind.PDF -> {
                    val file = block.file
                    if (file == null) {
                        MissingFile()
                    } else {
                        PdfView(file = file, modifier = Modifier.fillMaxWidth())
                    }
                }

                DocumentRenderer.Kind.WORD, DocumentRenderer.Kind.SHEET -> {
                    val file = block.file
                    if (file == null) {
                        MissingFile()
                    } else {
                        ExternalDocumentRow(file = file)
                    }
                }

                DocumentRenderer.Kind.IMAGE -> {
                    val file = block.file
                    if (file == null) {
                        MissingFile()
                    } else {
                        AsyncImage(
                            model = file,
                            contentDescription = uiState.title,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MoshiShapeSmall)
                        )
                    }
                }

                DocumentRenderer.Kind.AUDIO -> {
                    val file = block.file
                    if (file == null) {
                        MissingFile()
                    } else {
                        AudioPlayer(file = file, modifier = Modifier.clip(MoshiShapeSmall))
                    }
                }

                DocumentRenderer.Kind.MARKDOWN -> HtmlView(
                    html = remember(block.text, dark) {
                        HtmlRenderer.page(block.text, dark)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                DocumentRenderer.Kind.TEXT, DocumentRenderer.Kind.UNSUPPORTED -> HtmlView(
                    html = remember(block.text, dark, uiState.hitRange) {
                        HtmlRenderer.textPage(block.text, dark, uiState.hitRange)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HitSnippetCard(snippet: String, keyword: String) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.detail_hit_heading),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = highlightSnippet(snippet = snippet, keyword = keyword),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun highlightSnippet(snippet: String, keyword: String): AnnotatedString {
    val trimmed = snippet.take(HIT_SNIPPET_CHARS)
    if (keyword.isBlank()) return AnnotatedString(trimmed)
    val highlightColor = MaterialTheme.colorScheme.tertiary
    return buildAnnotatedString {
        var index = 0
        while (index < trimmed.length) {
            val found = trimmed.indexOf(keyword, index, ignoreCase = true)
            if (found < 0) {
                append(trimmed.substring(index))
                break
            }
            append(trimmed.substring(index, found))
            withStyle(SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold)) {
                append(trimmed.substring(found, found + keyword.length))
            }
            index = found + keyword.length
        }
    }
}

private const val HIT_SNIPPET_CHARS = 300

@Composable
private fun MissingFile() {
    Text(
        text = stringResource(R.string.ingest_file_missing),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun ExternalDocumentRow(file: File) {
    val context = LocalContext.current
    val sizeText = remember(file) { Formatter.formatFileSize(context, file.length()) }

    Surface(
        onClick = { openExternally(context, file) },
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.detail_open_external),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.detail_external_saved_fmt, sizeText),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun openExternally(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeOf(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.detail_no_viewer, Toast.LENGTH_SHORT).show()
    } catch (e: IllegalArgumentException) {
        Toast.makeText(context, R.string.detail_no_viewer, Toast.LENGTH_SHORT).show()
    }
}

private fun mimeOf(file: File): String {
    val extension = file.extension.lowercase()
    val resolved = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    if (!resolved.isNullOrBlank()) return resolved
    return when (extension) {
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "doc" -> "application/msword"
        "xls" -> "application/vnd.ms-excel"
        else -> "*/*"
    }
}

@Composable
private fun RelatedSection(
    related: List<DetailUiState.RelatedNote>,
    onClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.detail_related_heading),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        related.forEach { item ->
            Surface(
                onClick = { if (item.noteId.isNotEmpty()) onClick(item.noteId) },
                shape = MoshiShapeMedium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.score,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingBody() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.detail_missing),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { }) {
                Text(stringResource(R.string.detail_back))
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    onBack: () -> Unit,
    canEdit: Boolean,
    isEditing: Boolean,
    onEditClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onDeleteClick: (() -> Unit)?
) {
    PageTopBar(titleRes = R.string.detail_title, backDescRes = R.string.detail_back, onBack = onBack)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        if (canEdit && !isEditing) {
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.detail_edit)
                )
            }
        }
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
                if (onDeleteClick != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.detail_delete)) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                        },
                        onClick = onDeleteClick
                    )
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun DetailPageScreenPreview() {
    MoshiTheme {
        DetailPageScreen(uiState = sampleDetailUiState(androidx.compose.ui.platform.LocalContext.current), onBack = {})
    }
}
