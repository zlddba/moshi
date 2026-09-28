package dev.zlddba.moshiapp.data.repo

import android.content.Context
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.data.prefs.SeedPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object HelpSeeder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        val appContext = context.applicationContext
        val prefs = SeedPrefs(appContext)
        if (prefs.isHelpSeeded()) return
        scope.launch {
            try {
                val text = appContext.assets.open(ASSET_NAME)
                    .bufferedReader()
                    .use { it.readText() }
                IngestRepository.importText(
                    context = appContext,
                    text = text,
                    type = NoteEntity.TYPE_TEXT,
                    fallbackTitle = FALLBACK_TITLE
                )
                prefs.markHelpSeeded()
            } catch (t: Throwable) {
            }
        }
    }

    private const val ASSET_NAME = "help_manual.md"
    private const val FALLBACK_TITLE = "使用说明"
}
