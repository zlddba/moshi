package dev.zlddba.moshiapp.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SeedPrefs(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isHelpSeeded(): Boolean =
        preferences.getBoolean(KEY_HELP_SEEDED, false)

    fun markHelpSeeded() {
        preferences.edit { putBoolean(KEY_HELP_SEEDED, true) }
    }

    private companion object {
        const val PREFS_NAME = "moshi_seed"
        const val KEY_HELP_SEEDED = "help_note_seeded"
    }
}
