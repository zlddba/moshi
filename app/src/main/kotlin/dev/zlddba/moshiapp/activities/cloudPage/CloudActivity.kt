package dev.zlddba.moshiapp.activities.cloudPage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.prefs.CloudConfigPrefs
import dev.zlddba.moshiapp.engine.cloud.OpenAiCloudGateway
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import kotlinx.coroutines.launch

class CloudActivity : ComponentActivity() {

    private val viewModel: CloudViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CloudViewModel(
                        CloudConfigPrefs(applicationContext),
                        OpenAiCloudGateway()
                    ) as T
                }
            }
        )[CloudViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoshiTheme {
                val uiState by viewModel.cloudUiState.collectAsState()
                CloudPageScreen(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    onBack = { finish() }
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        CloudViewModel.CloudEffect.Saved -> {
                            Toast.makeText(
                                this@CloudActivity,
                                R.string.cloud_saved,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CloudActivity::class.java))
        }
    }
}
