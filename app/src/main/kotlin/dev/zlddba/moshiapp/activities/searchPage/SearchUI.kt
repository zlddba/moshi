package dev.zlddba.moshiapp.activities.searchPage

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.ui.theme.MoshiShapeLarge
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiShapePill
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

@Composable
fun SearchPageScreen(
    uiState: SearchViewModel.SearchUiState,
    onBack: () -> Unit,
    onEvent: (SearchViewModel.SearchEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        PageTopBar(
            titleRes = R.string.search_title,
            backDescRes = R.string.search_back,
            onBack = onBack
        )
        SearchField(
            query = uiState.query,
            onQueryChange = { onEvent(SearchViewModel.SearchEvent.QueryChanged(it)) },
            onSubmit = { onEvent(SearchViewModel.SearchEvent.Submit) }
        )
        when {
            uiState.isSearching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            !uiState.searched -> {
                Text(
                    text = stringResource(R.string.search_idle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 48.dp)
                )
            }

            uiState.results.isEmpty() -> {
                SearchRefusalCard(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }

            else -> {
                SearchResultList(
                    uiState = uiState,
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun SearchField(
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
                contentDescription = stringResource(R.string.search_title)
            )
        },
        trailingIcon = {
            IconButton(onClick = onSubmit) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = stringResource(R.string.search_submit)
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
private fun SearchRefusalCard(modifier: Modifier = Modifier) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.chat_refusal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
        )
    }
}

@Composable
private fun SearchResultList(
    uiState: SearchViewModel.SearchUiState,
    onEvent: (SearchViewModel.SearchEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val query = uiState.query.trim()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.search_result_count, uiState.results.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        items(
            items = uiState.results,
            key = { it.chunkId }
        ) { result ->
            SearchResultCard(
                result = result,
                query = query,
                onClick = {
                    onEvent(SearchViewModel.SearchEvent.ItemClick(result))
                }
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchViewModel.SearchUiState.SearchResult,
    query: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.noteTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val scorePercent = (result.score * 100).toInt().coerceIn(0, 100)
                Text(
                    text = stringResource(R.string.search_score, scorePercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(MoshiShapePill)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val highlightColor = MaterialTheme.colorScheme.primary
            val snippet = buildAnnotatedString {
                append(result.text)
                if (query.isNotBlank()) {
                    var index = result.text.indexOf(query, ignoreCase = true)
                    while (index >= 0) {
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor),
                            index,
                            index + query.length
                        )
                        index = result.text.indexOf(
                            query,
                            index + query.length,
                            ignoreCase = true
                        )
                    }
                }
            }
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
            val pageNo = result.pageNo
            if (pageNo != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.detail_page, pageNo),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .clip(MoshiShapePill)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun SearchPageScreenPreview() {
    MoshiTheme {
        SearchPageScreen(
            uiState = SearchViewModel.SearchUiState(
                query = "sqlite",
                searched = true,
                results = listOf(
                    SearchViewModel.SearchUiState.SearchResult(
                        chunkId = 1,
                        noteId = "note-1",
                        noteTitle = "sqlite-vec 使用笔记",
                        text = "通过 SQL 扩展方式加载 sqlite-vec 后，即可创建 vec0 虚拟表，虚拟表需要预先声明向量维度与距离度量。",
                        pageNo = 3,
                        score = 0.86f
                    ),
                    SearchViewModel.SearchUiState.SearchResult(
                        chunkId = 2,
                        noteId = "note-2",
                        noteTitle = "向量检索调研",
                        text = "向量列不支持常规索引，写入量大时建议批量事务提交。",
                        pageNo = null,
                        score = 0.62f
                    )
                )
            ),
            onBack = {},
            onEvent = {}
        )
    }
}
