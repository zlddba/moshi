package dev.zlddba.moshiapp.activities.privacyPage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import kotlinx.coroutines.launch

class ModelActivity : ComponentActivity() {

    private var pendingLocalId: String? = null

    private val viewModel: ModelViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ModelViewModel(applicationContext) as T
                }
            }
        )[ModelViewModel::class.java]
    }

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val id = pendingLocalId
        pendingLocalId = null
        if (uri == null || id == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.onEvent(ModelViewModel.ModelEvent.LoadLocal(id, uri.toString()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoshiTheme {
                val uiState by viewModel.modelUiState.collectAsState()
                ModelPageScreen(
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
                        is ModelViewModel.ModelEffect.ShowToast -> {
                            val text = if (effect.argRes != null) {
                                getString(effect.messageRes, getString(effect.argRes))
                            } else {
                                getString(effect.messageRes)
                            }
                            Toast.makeText(this@ModelActivity, text, Toast.LENGTH_SHORT).show()
                        }

                        is ModelViewModel.ModelEffect.PickLocalFolder -> {
                            pendingLocalId = effect.id
                            Toast.makeText(
                                this@ModelActivity,
                                getString(dev.zlddba.moshiapp.R.string.model_local_hint),
                                Toast.LENGTH_SHORT
                            ).show()
                            pickFolder.launch(null)
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ModelActivity::class.java))
        }
    }
}
