package dev.zlddba.moshiapp.activities.ocrPage

import android.app.Activity
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

class OcrActivity : ComponentActivity() {

    private val viewModel: OcrViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OcrViewModel(applicationContext) as T
                }
            }
        )[OcrViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.onEvent(OcrViewModel.OcrEvent.Init(intent.getStringExtra(EXTRA_IMAGE_URI)))
        setContent {
            MoshiTheme {
                val uiState by viewModel.uiState.collectAsState()
                OcrPageScreen(
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
                        OcrViewModel.OcrEffect.Close -> finish()

                        OcrViewModel.OcrEffect.CloseWithResult -> {
                            setResult(Activity.RESULT_OK)
                            finish()
                        }

                        is OcrViewModel.OcrEffect.ShowToast -> {
                            Toast.makeText(
                                this@OcrActivity,
                                effect.messageRes,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_IMAGE_URI = "image_uri"

        fun createIntent(context: Context, imageUri: String): Intent =
            Intent(context, OcrActivity::class.java).putExtra(EXTRA_IMAGE_URI, imageUri)
    }
}
