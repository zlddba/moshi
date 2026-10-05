package dev.zlddba.moshiapp.activities.lockPage

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
import dev.zlddba.moshiapp.activities.firstLaunchPage.FirstLaunchActivity
import dev.zlddba.moshiapp.activities.mainPage.MainActivity
import dev.zlddba.moshiapp.data.prefs.FirstLaunchPrefs
import dev.zlddba.moshiapp.data.prefs.SecurityPrefs
import dev.zlddba.moshiapp.data.repo.IngestRepository
import dev.zlddba.moshiapp.domain.security.AppLock
import dev.zlddba.moshiapp.domain.security.BiometricUnlock
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LockActivity : ComponentActivity() {

    private val viewModel: LockViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LockViewModel(applicationContext) as T
                }
            }
        )[LockViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.onEvent(
            LockViewModel.LockEvent.Start(intent.getStringExtra(EXTRA_MODE))
        )
        setContent {
            MoshiTheme {
                val uiState by viewModel.uiState.collectAsState()
                LockPageScreen(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    onCancel = { finish() }
                )
            }
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (viewModel.uiState.value.stage != LockViewModel.Stage.Unlock) {
                        finish()
                    }
                }
            }
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        LockViewModel.LockEffect.Unlocked -> finishAndRoute()

                        LockViewModel.LockEffect.CredentialStored -> {
                            setResult(RESULT_OK)
                            finish()
                        }

                        LockViewModel.LockEffect.RequestBiometric -> requestBiometric()

                        LockViewModel.LockEffect.ResetLibrary -> confirmReset()

                        is LockViewModel.LockEffect.ShowToast -> Toast.makeText(
                            applicationContext,
                            effect.messageRes,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun requestBiometric() {
        BiometricUnlock.authenticate(
            activity = this,
            title = getString(R.string.lock_biometric_title),
            subtitle = getString(R.string.lock_biometric_subtitle),
            negative = getString(R.string.lock_cancel),
            onSuccess = { viewModel.onEvent(LockViewModel.LockEvent.BiometricSucceeded) },
            onFailed = {}
        )
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.lock_forgot)
            .setMessage(R.string.lock_forgot_confirm)
            .setPositiveButton(R.string.lock_reset_confirm) { _, _ -> resetLibrary() }
            .setNegativeButton(R.string.lock_cancel, null)
            .show()
    }

    private fun resetLibrary() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { IngestRepository.clearAll(applicationContext) }
            }
            SecurityPrefs(applicationContext)
                .setLockMethod(SecurityPrefs.METHOD_NONE, null, null)
            AppLock.markUnlocked()
            Toast.makeText(applicationContext, R.string.lock_reset_done, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun finishAndRoute() {
        if (intent.getBooleanExtra(EXTRA_ROUTE, false)) {
            val next = if (FirstLaunchPrefs(this).isFirstLaunchCompleted()) {
                MainActivity::class.java
            } else {
                FirstLaunchActivity::class.java
            }
            startActivity(Intent(this, next))
        }
        finish()
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_ROUTE = "route"

        fun routeIntent(context: Context): Intent =
            Intent(context, LockActivity::class.java).putExtra(EXTRA_ROUTE, true)

        fun setupIntent(context: Context, method: String): Intent =
            Intent(context, LockActivity::class.java).putExtra(EXTRA_MODE, method)
    }
}
