package dev.zlddba.moshiapp.activities.cloudPage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

class CloudActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoshiTheme {
                CloudPageScreen(onBack = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CloudActivity::class.java))
        }
    }
}
