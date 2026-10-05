package dev.zlddba.moshiapp.ui

import android.util.Log
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.ingest.parse.IngestException

private const val TAG = "MoshiIngest"

object IngestMessages {

    fun errorOf(kind: IngestException.Kind): Int {
        Log.w(TAG, "ingest failed ${kind.errorCode.code}")
        return when (kind) {
            IngestException.Kind.UNSUPPORTED -> R.string.ingest_unsupported
            IngestException.Kind.ENCRYPTED -> R.string.ingest_encrypted
            IngestException.Kind.TOO_LARGE -> R.string.ingest_too_large
            IngestException.Kind.EMPTY -> R.string.ingest_empty
            IngestException.Kind.PARSE_FAILED -> R.string.ingest_parse_failed
            IngestException.Kind.IO -> R.string.ingest_failed
        }
    }
}
