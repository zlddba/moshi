package dev.zlddba.moshiapp.activities.firstLaunchPage

import android.content.Intent
import android.os.Bundle
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
import dev.zlddba.moshiapp.activities.mainPage.MainActivity
import dev.zlddba.moshiapp.data.prefs.FirstLaunchPrefs
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import kotlinx.coroutines.launch

class FirstLaunchActivity : ComponentActivity() {

    private val viewModel: FirstLaunchViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FirstLaunchViewModel(FirstLaunchPrefs(applicationContext)) as T
                }
            }
        )[FirstLaunchViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoshiTheme {
                val uiState by viewModel.firstLaunchUiState.collectAsState()
                FirstLaunchPageScreen(
                    uiState = uiState,
                    onEvent = viewModel::onEvent
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        FirstLaunchViewModel.FirstLaunchEffect.NavigateToMain -> {
                            startActivity(Intent(this@FirstLaunchActivity, MainActivity::class.java))
                            finish()
                        }
                    }
                }
            }
        }
    }
}
