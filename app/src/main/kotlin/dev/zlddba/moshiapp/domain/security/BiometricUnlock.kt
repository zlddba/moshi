package dev.zlddba.moshiapp.domain.security

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

object BiometricUnlock {

    private const val FEATURE_FINGERPRINT = "android.hardware.fingerprint"
    private const val FEATURE_BIOMETRIC = "android.hardware.biometric"

    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            val manager = context.packageManager
            manager.hasSystemFeature(FEATURE_FINGERPRINT) ||
                manager.hasSystemFeature(FEATURE_BIOMETRIC)
        } catch (e: Throwable) {
            false
        }
    }

    fun authenticate(
        activity: Activity,
        title: String,
        subtitle: String,
        negative: String,
        onSuccess: () -> Unit,
        onFailed: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onFailed()
            return
        }
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButton(negative, executor) { _, _ -> onFailed() }
                .build()
            prompt.authenticate(
                CancellationSignal(),
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult?
                    ) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        onFailed()
                    }

                    override fun onAuthenticationFailed() {
                    }
                }
            )
        } catch (e: Throwable) {
            onFailed()
        }
    }
}
