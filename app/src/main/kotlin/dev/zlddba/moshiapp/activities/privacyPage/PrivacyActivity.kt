package dev.zlddba.moshiapp.activities.privacyPage

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
import dev.zlddba.moshiapp.activities.lockPage.LockActivity
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import kotlinx.coroutines.launch

class PrivacyActivity : ComponentActivity() {

    private val viewModel: PrivacyViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PrivacyViewModel(applicationContext) as T
                }
            }
        )[PrivacyViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.onEvent(PrivacyViewModel.PrivacyEvent.Reload)
        setContent {
            MoshiTheme {
                val uiState by viewModel.uiState.collectAsState()
                PrivacyPageScreen(
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
                        is PrivacyViewModel.PrivacyEffect.ShowToast -> Toast.makeText(
                            applicationContext,
                            effect.messageRes,
                            Toast.LENGTH_SHORT
                        ).show()

                        is PrivacyViewModel.PrivacyEffect.OpenLockSetup -> startActivity(
                            LockActivity.setupIntent(this@PrivacyActivity, effect.method)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onEvent(PrivacyViewModel.PrivacyEvent.Reload)
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PrivacyActivity::class.java))
        }
    }
}
