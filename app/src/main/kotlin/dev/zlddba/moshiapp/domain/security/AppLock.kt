package dev.zlddba.moshiapp.domain.security

import android.content.Context
import dev.zlddba.moshiapp.data.prefs.SecurityPrefs

object AppLock {

    @Volatile
    private var unlocked = false

    @Volatile
    private var backgroundAt = 0L

    fun isLockEnabled(context: Context): Boolean =
        SecurityPrefs(context.applicationContext).isLockEnabled()

    fun lockMethod(context: Context): String =
        SecurityPrefs(context.applicationContext).lockMethod()

    fun isUnlocked(): Boolean = unlocked

    fun markUnlocked() {
        unlocked = true
        backgroundAt = 0L
    }

    fun markLocked() {
        unlocked = false
    }

    fun onEnterBackground(context: Context) {
        backgroundAt = System.currentTimeMillis()
        CryptoManager.clearCache(context)
        CryptoManager.clearSession()
    }

    fun shouldLockOnForeground(context: Context): Boolean {
        val prefs = SecurityPrefs(context.applicationContext)
        if (!prefs.isLockEnabled()) return false
        if (!unlocked) return true
        if (!prefs.isAutoLockEnabled()) return false
        val since = backgroundAt
        if (since <= 0L) return false
        val elapsed = (System.currentTimeMillis() - since) / 1000L
        return elapsed >= prefs.autoLockDelaySeconds().toLong()
    }
}
