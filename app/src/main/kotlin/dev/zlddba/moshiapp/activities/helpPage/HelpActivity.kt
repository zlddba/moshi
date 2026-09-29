package dev.zlddba.moshiapp.activities.helpPage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

class HelpActivity : ComponentActivity() {

    private val manual: String by lazy {
        try {
            assets.open(MANUAL_ASSET).bufferedReader().use { it.readText() }
        } catch (e: Throwable) {
            ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoshiTheme {
                HelpPageScreen(
                    manual = manual,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        private const val MANUAL_ASSET = "help_manual.md"

        fun start(context: Context) {
            context.startActivity(Intent(context, HelpActivity::class.java))
        }
    }
}
