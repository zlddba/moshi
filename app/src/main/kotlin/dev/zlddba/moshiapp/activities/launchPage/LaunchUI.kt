package dev.zlddba.moshiapp.activities.launchPage

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

@Composable
fun LaunchPageScreen(version: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.fillMaxHeight(.2f))
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(
                            RoundedCornerShape(20.dp)
                        )
                )
                Text(
                    text = stringResource(R.string.app_name_zh),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            if (version.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.launch_page_version) + version,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun LaunchPageScreenPreview() {
    MoshiTheme {
        LaunchPageScreen(version = "2")
    }
}