package dev.zlddba.moshiapp.activities.voicePage

import android.Manifest
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

class VoiceActivity : ComponentActivity() {

    private val viewModel: VoiceViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return VoiceViewModel(applicationContext) as T
                }
            }
        )[VoiceViewModel::class.java]
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onEvent(VoiceViewModel.VoiceEvent.PermissionResult(granted))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoshiTheme {
                val uiState by viewModel.voiceUiState.collectAsState()
                VoicePageScreen(
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
                        is VoiceViewModel.VoiceEffect.ShowToast -> {
                            Toast.makeText(
                                this@VoiceActivity,
                                effect.messageRes,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        VoiceViewModel.VoiceEffect.RequestAudioPermission -> {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }

                        VoiceViewModel.VoiceEffect.Close -> finish()
                    }
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, VoiceActivity::class.java))
        }
    }
}
