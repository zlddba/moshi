package dev.zlddba.moshiapp.data.repo

import android.content.Context
import android.util.Log
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.data.prefs.SeedPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object HelpSeeder {

    private const val TAG = "HelpSeeder"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val existing = MoshiDatabase.get(appContext).noteDao().builtinCount()
                if (existing > 0) {
                    Log.i(TAG, "seed skipped: builtin notes=$existing")
                    return@launch
                }
                val text = appContext.assets.open(ASSET_NAME)
                    .bufferedReader()
                    .use { it.readText() }
                val summary = IngestRepository.importText(
                    context = appContext,
                    text = text,
                    type = NoteEntity.TYPE_TEXT,
                    fallbackTitle = FALLBACK_TITLE,
                    isBuiltIn = true
                )
                SeedPrefs(appContext).markHelpSeeded()
                Log.i(TAG, "seed ok chars=${text.length} chunks=${summary.chunkCount}")
            } catch (t: Throwable) {
                Log.e(TAG, "seed failed", t)
            }
        }
    }

    private const val ASSET_NAME = "help_manual.md"
    private const val FALLBACK_TITLE = "使用说明"
}
