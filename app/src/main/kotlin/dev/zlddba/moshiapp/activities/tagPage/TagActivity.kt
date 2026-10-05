package dev.zlddba.moshiapp.activities.tagPage

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
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import kotlinx.coroutines.launch

class TagActivity : ComponentActivity() {

    private val viewModel: TagViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TagViewModel(applicationContext) as T
                }
            }
        )[TagViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoshiTheme {
                val uiState by viewModel.uiState.collectAsState()
                TagPageScreen(
                    uiState = uiState,
                    onBack = { finish() },
                    onEvent = viewModel::onEvent
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is TagViewModel.TagEffect.ShowToast -> Toast.makeText(
                            this@TagActivity,
                            effect.messageRes,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, TagActivity::class.java))
        }
    }
}
