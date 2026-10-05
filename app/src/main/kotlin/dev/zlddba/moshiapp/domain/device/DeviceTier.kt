package dev.zlddba.moshiapp.domain.device

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import dev.zlddba.moshiapp.data.prefs.ModelPrefs
import dev.zlddba.moshiapp.ingest.models.ModelCatalog
import dev.zlddba.moshiapp.ingest.models.ModelFileManager

object DeviceTier {

    private const val TAG = "DeviceTier"
    private const val FLAGSHIP_TOTAL_BYTES = 8L * 1024 * 1024 * 1024
    private const val MID_TOTAL_BYTES = 6L * 1024 * 1024 * 1024
    private const val BYTES_PER_MB = 1024L * 1024L

    enum class Tier { FLAGSHIP, MID, LOW }

    fun detect(context: Context): Tier {
        val appContext = context.applicationContext
        val total = totalMemoryBytes(appContext)
        return when {
            isLowRamDevice(appContext) -> Tier.LOW
            total in 1L..<MID_TOTAL_BYTES -> Tier.LOW
            total < FLAGSHIP_TOTAL_BYTES -> Tier.MID
            else -> Tier.FLAGSHIP
        }
    }

    fun totalMemoryMb(context: Context): Long =
        totalMemoryBytes(context.applicationContext) / BYTES_PER_MB

    fun applyAutoDowngrade(context: Context): Boolean {
        val appContext = context.applicationContext
        val tier = detect(appContext)
        val prefs = ModelPrefs(appContext)
        val current = prefs.currentLlm()
        Log.i(
            TAG,
            "tier=$tier totalMb=${totalMemoryMb(appContext)} currentLlm=$current"
        )
        if (tier != Tier.LOW) return false
        if (current == ModelCatalog.QWEN) return false
        if (!ModelFileManager.isReady(appContext, ModelCatalog.QWEN)) {
            Log.w(TAG, "low tier but qwen not ready, keep $current")
            return false
        }
        prefs.setCurrentLlm(ModelCatalog.QWEN)
        Log.i(TAG, "auto downgrade $current -> ${ModelCatalog.QWEN}")
        return true
    }

    private fun totalMemoryBytes(context: Context): Long = try {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (manager == null) {
            0L
        } else {
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            info.totalMem
        }
    } catch (e: Throwable) {
        Log.w(TAG, "totalMemory unavailable", e)
        0L
    }

    private fun isLowRamDevice(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        manager?.isLowRamDevice ?: false
    } catch (e: Throwable) {
        false
    }
}
