package dev.zlddba.moshiapp.ui.doc

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun AudioPlayer(file: File, modifier: Modifier = Modifier) {
    var player by remember(file.path) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file.path) { mutableStateOf(false) }

    fun release() {
        val current = player
        player = null
        playing = false
        current?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
            }
            it.release()
        }
    }

    DisposableEffect(file.path) {
        onDispose { release() }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            TextButton(
                onClick = {
                    if (playing) {
                        release()
                        return@TextButton
                    }
                    val created = MediaPlayer()
                    try {
                        created.setDataSource(file.absolutePath)
                        created.prepare()
                        created.setOnCompletionListener { release() }
                        created.start()
                    } catch (e: Exception) {
                        created.release()
                        return@TextButton
                    }
                    player = created
                    playing = true
                }
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放"
                )
                Text(if (playing) "暂停" else "播放", modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
