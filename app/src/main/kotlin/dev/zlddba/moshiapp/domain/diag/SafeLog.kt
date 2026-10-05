package dev.zlddba.moshiapp.domain.diag

import android.util.Log
import dev.zlddba.moshiapp.BuildConfig

object SafeLog {

    const val LIMIT_SHORT = 800
    const val LIMIT_TEXT = 1500
    const val LIMIT_LONG = 3000

    fun enabled(): Boolean = BuildConfig.DEBUG

    fun flat(raw: String, limit: Int = LIMIT_TEXT): String {
        if (raw.isEmpty()) return "<empty>"
        val flattened = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\n", " ⏎ ")
            .replace(Regex(" {2,}"), " ")
            .trim()
        return if (flattened.length <= limit) {
            flattened
        } else {
            flattened.take(limit) + "…[共${flattened.length}字]"
        }
    }

    fun out(tag: String, label: String, raw: String, limit: Int = LIMIT_TEXT) {
        if (!enabled()) return
        Log.i(tag, "$label ${flat(raw, limit)}")
    }
}
