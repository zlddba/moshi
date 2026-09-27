package dev.zlddba.moshiapp.activities.firstLaunchPage

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.firstLaunchPage.FirstLaunchViewModel.FirstLaunchEvent
import dev.zlddba.moshiapp.activities.firstLaunchPage.FirstLaunchViewModel.FirstLaunchUiState
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

private data class FirstLaunchPageItem(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int
)

private val firstLaunchPages = listOf(
    FirstLaunchPageItem(
        icon = Icons.Outlined.Shield,
        titleRes = R.string.first_launch_privacy_title,
        bodyRes = R.string.first_launch_privacy_body
    ),
    FirstLaunchPageItem(
        icon = Icons.Outlined.CloudSync,
        titleRes = R.string.first_launch_engine_title,
        bodyRes = R.string.first_launch_engine_body
    ),
    FirstLaunchPageItem(
        icon = Icons.AutoMirrored.Outlined.FactCheck,
        titleRes = R.string.first_launch_evidence_title,
        bodyRes = R.string.first_launch_evidence_body
    )
)

@Composable
fun FirstLaunchPageScreen(
    uiState: FirstLaunchUiState,
    onEvent: (FirstLaunchEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { uiState.pageCount })
    val currentOnEvent by rememberUpdatedState(onEvent)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            currentOnEvent(FirstLaunchEvent.PageChanged(page))
        }
    }
    LaunchedEffect(uiState.currentPage) {
        if (pagerState.settledPage != uiState.currentPage) {
            pagerState.animateScrollToPage(uiState.currentPage)
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onEvent(FirstLaunchEvent.SkipClicked) }) {
                    Text(text = stringResource(R.string.first_launch_skip))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                FirstLaunchPageContent(item = firstLaunchPages[page])
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(uiState.pageCount) { index ->
                    val isActive = index == uiState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(if (isActive) 20.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Button(
                onClick = { onEvent(FirstLaunchEvent.NextClicked) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp)),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text(
                    text = stringResource(
                        if (uiState.isLastPage) R.string.first_launch_start
                        else R.string.first_launch_next
                    ),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.isLastPage) {
                TextButton(
                    onClick = { onEvent(FirstLaunchEvent.SkipClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.first_launch_later_cloud),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            )
        }
    }
}

@Composable
private fun FirstLaunchPageContent(
    item: FirstLaunchPageItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = stringResource(item.titleRes),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(item.bodyRes),
            fontSize = 15.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun FirstLaunchPageScreenPreview() {
    MoshiTheme {
        FirstLaunchPageScreen(
            uiState = FirstLaunchUiState(),
            onEvent = {}
        )
    }
}
