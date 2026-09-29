package dev.zlddba.moshiapp.activities.mainPage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.ui.theme.MoshiShapeLarge
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

data class HomeUiState(
    val cards: List<NoteCard> = emptyList(),
    val filter: Int = 0,
    val loaded: Boolean = false
) {
    data class NoteCard(
        val noteId: String,
        val type: String,
        val title: String,
        val summary: String,
        val tag: String,
        val time: String
    )
}

private val homeFilters = listOf(
    R.string.home_filter_timeline,
    R.string.home_filter_category
)

private fun sampleHomeUiState(): HomeUiState = HomeUiState(
    loaded = true,
    cards = listOf(
        HomeUiState.NoteCard(
            noteId = "demo-1",
            type = NoteEntity.TYPE_TEXT,
            title = "向量数据库入门笔记",
            summary = "记录 sqlite-vec 的索引结构、相似度计算与检索流程…",
            tag = "数据库课程资料",
            time = "2026-09-20 14:30"
        ),
        HomeUiState.NoteCard(
            noteId = "demo-2",
            type = NoteEntity.TYPE_PDF,
            title = "机器学习课程讲义",
            summary = "监督学习、损失函数与梯度下降的核心推导过程…",
            tag = "课堂讲义",
            time = "2026-09-18 09:12"
        ),
        HomeUiState.NoteCard(
            noteId = "demo-3",
            type = NoteEntity.TYPE_IMAGE_OCR,
            title = "白板讨论截图",
            summary = "关于端侧模型量化与蒸馏的方案对比记录…",
            tag = "",
            time = "2026-09-15 20:45"
        ),
        HomeUiState.NoteCard(
            noteId = "demo-4",
            type = NoteEntity.TYPE_AUDIO,
            title = "组会语音记录",
            summary = "本周进度同步与下周分工安排的转写文本…",
            tag = "组会",
            time = "2026-09-12 16:08"
        )
    )
)

@Composable
fun MainHomeScreen(
    uiState: HomeUiState,
    onEvent: (MainHomeViewModel.HomeEvent) -> Unit,
    onNoteClick: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    isCloudEngine: Boolean = false,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<HomeUiState.NoteCard?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        HomeTopBar(isCloudEngine = isCloudEngine)
        HomeSearchBar(
            query = query,
            onQueryChange = { query = it },
            onSubmit = { onSearchSubmit(query) }
        )
        HomeFilterRow(
            selectedFilter = uiState.filter,
            onFilterClick = { onEvent(MainHomeViewModel.HomeEvent.FilterChanged(it)) }
        )
        if (!uiState.loaded) {
            Spacer(modifier = Modifier.height(1.dp))
        } else if (uiState.cards.isEmpty()) {
            HomeEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.cards) { card ->
                    HomeNoteCard(
                        card = card,
                        onClick = { onNoteClick(card.noteId) },
                        onLongClick = { deleteTarget = card }
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.detail_delete)) },
            text = { Text(stringResource(R.string.detail_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(
                            MainHomeViewModel.HomeEvent.DeleteRequested(
                                noteId = target.noteId,
                                title = target.title
                            )
                        )
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.detail_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            }
        )
    }
}

@Composable
private fun HomeTopBar(isCloudEngine: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.app_name_zh),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        HomeEngineBadge(isCloudEngine = isCloudEngine)
    }
}

@Composable
private fun HomeEngineBadge(isCloudEngine: Boolean) {
    val container = if (isCloudEngine) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.primaryContainer
    val onContainer = if (isCloudEngine) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onPrimaryContainer
    val icon: ImageVector = if (isCloudEngine) Icons.Outlined.CloudSync
    else Icons.Outlined.Shield
    val label = if (isCloudEngine) R.string.home_badge_cloud
    else R.string.home_badge_local

    Surface(
        shape = MoshiShapePill,
        color = container
    ) {
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
            Spacer(modifier = Modifier.width(5.dp))
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
private fun HomeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.home_search_hint)
            )
        },
        trailingIcon = {
            IconButton(onClick = onSubmit) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = stringResource(R.string.home_search_submit)
                )
            }
        },
        singleLine = true,
        shape = MoshiShapeLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() })
    )
}

@Composable
private fun HomeFilterRow(
    selectedFilter: Int,
    onFilterClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        homeFilters.forEachIndexed { index, labelRes ->
            val selected = index == selectedFilter
            Surface(
                onClick = { onFilterClick(index) },
                shape = MoshiShapePill,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
                border = if (selected) null
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private fun typeVisual(type: String): Pair<ImageVector, Int> = when (type) {
    NoteEntity.TYPE_PDF -> Icons.Outlined.PictureAsPdf to R.string.home_card_type_pdf
    NoteEntity.TYPE_IMAGE_OCR -> Icons.Outlined.Image to R.string.home_card_type_image
    NoteEntity.TYPE_AUDIO -> Icons.Outlined.Mic to R.string.home_card_type_voice
    else -> Icons.AutoMirrored.Outlined.Article to R.string.home_card_type_text
}

@Composable
private fun HomeNoteCard(
    card: HomeUiState.NoteCard,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val (icon, typeRes) = typeVisual(card.type)
    val isPdf = card.type == NoteEntity.TYPE_PDF
    val tileColor = if (isPdf) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.primaryContainer
    val tileTint = if (isPdf) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(MoshiShapeMedium)
                    .background(tileColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = tileTint
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(typeRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPdf) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MoshiShapePill)
                            .background(tileColor)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = card.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (card.tag.isNotBlank()) {
                        Text(
                            text = card.tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(MoshiShapePill)
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Text(
                        text = card.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(MoshiShapeLarge)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun MainHomeScreenPreview() {
    MoshiTheme {
        MainHomeScreen(
            uiState = sampleHomeUiState(),
            onEvent = {},
            onNoteClick = {},
            onSearchSubmit = {}
        )
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun MainHomeEmptyScreenPreview() {
    MoshiTheme {
        HomeEmptyState()
    }
}
