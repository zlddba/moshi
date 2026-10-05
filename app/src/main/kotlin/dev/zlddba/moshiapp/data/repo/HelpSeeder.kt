package dev.zlddba.moshiapp.data.repo

import android.content.Context
import android.util.Log
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.data.prefs.SeedPrefs
import java.security.MessageDigest
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
                val text = appContext.assets.open(ASSET_NAME)
                    .bufferedReader()
                    .use { it.readText() }
                val hash = sha256(text)
                val seedPrefs = SeedPrefs(appContext)
                val noteDao = MoshiDatabase.get(appContext).noteDao()
                val existing = noteDao.builtinCount()
                if (existing > 0 && seedPrefs.helpSeedHash() == hash) {
                    Log.i(TAG, "seed skipped: builtin=$existing hash=${hash.take(8)}")
                    return@launch
                }
                val stale = if (existing > 0) {
                    Log.i(TAG, "help manual changed, builtin=$existing hash=${hash.take(8)}")
                    noteDao.recentAll().filter { it.isBuiltIn }
                } else {
                    emptyList()
                }
                val summary = IngestRepository.importText(
                    context = appContext,
                    text = text,
                    type = NoteEntity.TYPE_TEXT,
                    fallbackTitle = FALLBACK_TITLE,
                    isBuiltIn = true
                )
                seedPrefs.markHelpSeeded(hash)
                Log.i(
                    TAG,
                    "seed ok chars=${text.length} chunks=${summary.chunkCount} hash=${hash.take(8)}"
                )
                for (note in stale) {
                    try {
                        IngestRepository.deleteNote(appContext, note.id)
                    } catch (t: Throwable) {
                        Log.w(TAG, "stale builtin delete failed id=${note.id}", t)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "seed failed", t)
            }
        }
    }

    private fun sha256(text: String): String = try {
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    } catch (e: Throwable) {
        text.length.toString()
    }

    private const val ASSET_NAME = "help_manual.md"
    private const val FALLBACK_TITLE = "使用说明"
}
