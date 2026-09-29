package dev.zlddba.moshiapp.activities.privacyPage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.repo.IngestRepository
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

class StorageActivity : ComponentActivity() {

    private val viewModel: StorageViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StorageViewModel(applicationContext) as T
                }
            }
        )[StorageViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.onEvent(StorageViewModel.StorageEvent.Refresh)
        setContent {
            MoshiTheme {
                val uiState by viewModel.uiState.collectAsState()
                val context = LocalContext.current
                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri != null) {
                        viewModel.onEvent(StorageViewModel.StorageEvent.ExportChosen(uri))
                    }
                }
                LaunchedEffect(Unit) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            StorageViewModel.StorageEffect.PickExportDestination -> {
                                exportLauncher.launch(IngestRepository.exportSuggestionName())
                            }

                            is StorageViewModel.StorageEffect.ShowToast -> {
                                val message = if (effect.arg >= 0) {
                                    context.getString(effect.messageRes, effect.arg)
                                } else {
                                    context.getString(effect.messageRes)
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                StoragePageScreen(
                    uiState = uiState,
                    onBack = { finish() },
                    onExport = {
                        viewModel.onEvent(StorageViewModel.StorageEvent.ExportRequested)
                    },
                    onClearConfirmed = {
                        viewModel.onEvent(StorageViewModel.StorageEvent.ClearConfirmed)
                    }
                )
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, StorageActivity::class.java))
        }
    }
}
