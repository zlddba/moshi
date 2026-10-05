package dev.zlddba.moshiapp.domain.security

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

object BiometricUnlock {

    private const val FEATURE_FINGERPRINT = "android.hardware.fingerprint"
    private const val FEATURE_BIOMETRIC = "android.hardware.biometric"
    private const val FEATURE_FACE = "android.hardware.biometric.face"
    private const val FEATURE_IRIS = "android.hardware.biometric.iris"

    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val result = canAuthenticate(context, withAuthenticators = true)
            if (result != null) return result
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            val result = canAuthenticate(context, withAuthenticators = false)
            if (result != null) return result
        }
        return hasBiometricHardware(context)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun canAuthenticate(context: Context, withAuthenticators: Boolean): Boolean? {
        val manager = try {
            context.getSystemService(Context.BIOMETRIC_SERVICE) as? BiometricManager
        } catch (e: Throwable) {
            null
        } ?: return null
        return try {
            @Suppress("DEPRECATION")
            val code = if (withAuthenticators) {
                manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            } else {
                manager.canAuthenticate()
            }
            code == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Throwable) {
            null
        }
    }

    private fun hasBiometricHardware(context: Context): Boolean = try {
        val manager = context.packageManager
        manager.hasSystemFeature(FEATURE_FINGERPRINT) ||
            manager.hasSystemFeature(FEATURE_BIOMETRIC) ||
            manager.hasSystemFeature(FEATURE_FACE) ||
            manager.hasSystemFeature(FEATURE_IRIS)
    } catch (e: Throwable) {
        false
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
