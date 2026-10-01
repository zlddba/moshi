package dev.zlddba.moshiapp.activities.sharePage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import dev.zlddba.moshiapp.activities.ocrPage.OcrActivity
import dev.zlddba.moshiapp.activities.textPage.TextActivity
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import kotlinx.coroutines.launch

class ShareActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ShareViewModel(applicationContext) as T
                }
            }
        )[ShareViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val stream = sharedStream()
        if (stream == null) {
            val text = sharedText()
            if (text != null) {
                startActivity(
                    Intent(this, TextActivity::class.java)
                        .putExtra(Intent.EXTRA_TEXT, text)
                        .putExtra(Intent.EXTRA_SUBJECT, sharedSubject())
                )
                finish()
                return
            }
        }
        viewModel.onEvent(
            ShareViewModel.ShareEvent.Init(
                uriString = stream?.toString(),
                suggestedTitle = sharedSubject()
            )
        )
        setContent {
            MoshiTheme {
                val uiState by viewModel.uiState.collectAsState()
                SharePageScreen(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    onClose = { finish() }
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is ShareViewModel.ShareEffect.ShowToast -> Toast.makeText(
                            applicationContext,
                            effect.messageRes,
                            Toast.LENGTH_SHORT
                        ).show()

                        is ShareViewModel.ShareEffect.OpenOcr -> {
                            startActivity(
                                OcrActivity.createIntent(this@ShareActivity, effect.imageUri)
                            )
                            finish()
                        }

                        ShareViewModel.ShareEffect.Close -> finish()
                    }
                }
            }
        }
    }

    private fun sharedStream(): Uri? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
    }.getOrNull()

    private fun sharedSubject(): String? =
        intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeUnless { it.isBlank() }

    private fun sharedText(): String? {
        val processed = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (!processed.isNullOrBlank()) return processed
        val sent = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        return sent?.takeUnless { it.isBlank() }
    }

    companion object {
        fun createIntent(context: Context, uri: Uri): Intent =
            Intent(context, ShareActivity::class.java).putExtra(Intent.EXTRA_STREAM, uri)
    }
}
