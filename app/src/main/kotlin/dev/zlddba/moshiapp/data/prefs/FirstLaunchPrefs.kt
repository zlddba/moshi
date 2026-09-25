package dev.zlddba.moshiapp.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class FirstLaunchPrefs(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFirstLaunchCompleted(): Boolean =
        preferences.getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)

    fun markFirstLaunchCompleted() {
        preferences.edit { putBoolean(KEY_FIRST_LAUNCH_COMPLETED, true) }
    }

    private companion object {
        const val PREFS_NAME = "moshi_first_launch"
        const val KEY_FIRST_LAUNCH_COMPLETED = "first_launch_completed"
    }
}
