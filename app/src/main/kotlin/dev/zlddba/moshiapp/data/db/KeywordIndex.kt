package dev.zlddba.moshiapp.data.db

import android.content.Context
import android.database.Cursor
import dev.zlddba.moshiapp.domain.security.CryptoManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase

object KeywordIndex {

    data class KwHit(
        val chunkId: Int,
        val noteId: String
    )

    private const val FTS_TABLE = "chunks_fts"
    private const val PLAIN_TABLE = "chunks_kw"
    private const val DATABASE_NAME = "keywords.db"
    private const val MAX_LIKE_TERMS = 8
    private const val MIN_LIKE_TERM = 2
    private const val MIN_FTS_TERM = 3

    @Volatile
    private var database: SQLiteDatabase? = null

    @Volatile
    private var ftsReady = false

    suspend fun search(context: Context, query: String, k: Int): List<KwHit> =
        withContext(Dispatchers.IO) {
            if (k <= 0 || query.isBlank()) return@withContext emptyList()
            val db = open(context) ?: return@withContext emptyList()
            val terms = termsOf(query)
            if (terms.isEmpty()) return@withContext emptyList()
            if (ftsReady) {
                val ftsHits = ftsSearch(db, terms, k)
                if (ftsHits.isNotEmpty()) return@withContext ftsHits
            }
            likeSearch(db, terms, k)
        }

    fun deleteNote(context: Context, noteId: String) {
        val db = open(context) ?: return
        for (table in listOf(FTS_TABLE, PLAIN_TABLE)) {
            try {
                db.execSQL("DELETE FROM $table WHERE note_id = ?", arrayOf<Any>(noteId))
            } catch (e: Throwable) {
            }
        }
    }

    fun clearAll(context: Context) {
        val db = open(context) ?: return
        for (table in listOf(FTS_TABLE, PLAIN_TABLE)) {
            try {
                db.execSQL("DELETE FROM $table")
            } catch (e: Throwable) {
            }
        }
    }

    fun reset() {
        synchronized(this) {
            try {
                database?.close()
            } catch (e: Throwable) {
            }
            database = null
            ftsReady = false
        }
    }

    fun insertChunks(context: Context, chunks: List<ChunkEntity>) {
        if (chunks.isEmpty()) return
        val db = open(context) ?: return
        val table = if (ftsReady) FTS_TABLE else PLAIN_TABLE
        val noteId = chunks.first().noteId
        try {
            db.execSQL("DELETE FROM $table WHERE note_id = ?", arrayOf<Any>(noteId))
        } catch (e: Throwable) {
        }
        for (chunk in chunks) {
            try {
                db.execSQL(
                    "INSERT INTO $table (chunk_id, note_id, text) VALUES (?, ?, ?)",
                    arrayOf<Any>(chunk.id, chunk.noteId, chunk.text)
                )
            } catch (e: Throwable) {
            }
        }
    }

    private fun open(context: Context): SQLiteDatabase? {
        database?.let { return it }
        synchronized(this) {
            database?.let { return it }
            if (!DatabaseCipher.ensureNative()) return null
            val appContext = context.applicationContext
            val directory = File(appContext.filesDir, "keywords")
            directory.mkdirs()
            val file = File(directory, DATABASE_NAME)
            val encrypt = CryptoManager.isEnabled(appContext)
            val passphrase = if (encrypt) CryptoManager.databasePassphrase(appContext) else null
            DatabaseCipher.prepare(file, encrypt, passphrase)
            val encrypted = DatabaseCipher.isEncryptedOnDisk(file) ||
                (encrypt && !file.exists() && passphrase != null)
            val key = if (encrypted) {
                passphrase?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            } else {
                ByteArray(0)
            }
            val created = try {
                SQLiteDatabase.openOrCreateDatabase(file, key, null, null, null)
            } catch (e: Throwable) {
                return null
            }
            ftsReady = try {
                created.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS $FTS_TABLE USING fts5(" +
                        "chunk_id UNINDEXED, note_id UNINDEXED, text, tokenize='trigram')"
                )
                true
            } catch (e: Throwable) {
                try {
                    created.execSQL(
                        "CREATE TABLE IF NOT EXISTS $PLAIN_TABLE (" +
                            "chunk_id INTEGER PRIMARY KEY, note_id TEXT NOT NULL, " +
                            "text TEXT NOT NULL)"
                    )
                } catch (inner: Throwable) {
                }
                false
            }
            database = created
            return created
        }
    }

    private fun ftsSearch(db: SQLiteDatabase, terms: List<String>, k: Int): List<KwHit> {
        val expression = terms.filter { it.length >= MIN_FTS_TERM }
            .joinToString(" OR ") { term -> "\"${term.replace("\"", "\"\"")}\"" }
        if (expression.isEmpty()) return emptyList()
        return try {
            readHits(
                db.rawQuery(
                    "SELECT chunk_id, note_id FROM $FTS_TABLE WHERE $FTS_TABLE MATCH ? " +
                        "ORDER BY rank LIMIT $k",
                    arrayOf(expression)
                )
            )
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun likeSearch(db: SQLiteDatabase, terms: List<String>, k: Int): List<KwHit> {
        val selected = terms.take(MAX_LIKE_TERMS)
        if (selected.isEmpty()) return emptyList()
        val table = if (ftsReady) FTS_TABLE else PLAIN_TABLE
        val patterns = selected.map { term -> likePattern(term) }
        val where = selected.joinToString(" OR ") { "text LIKE ? ESCAPE '\\'" }
        val hitsExpression = selected.joinToString(" + ") {
            "CASE WHEN text LIKE ? ESCAPE '\\' THEN 1 ELSE 0 END"
        }
        val sql = "SELECT chunk_id, note_id, $hitsExpression AS hits FROM $table " +
            "WHERE $where ORDER BY hits DESC, chunk_id LIMIT $k"
        val arguments = arrayOfNulls<String>(patterns.size * 2)
        for (index in patterns.indices) {
            arguments[index] = patterns[index]
            arguments[index + patterns.size] = patterns[index]
        }
        return try {
            readHits(db.rawQuery(sql, arguments))
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun readHits(cursor: Cursor): List<KwHit> {
        cursor.use {
            val hits = ArrayList<KwHit>(it.count)
            while (it.moveToNext()) {
                hits.add(KwHit(it.getInt(0), it.getString(1)))
            }
            return hits
        }
    }

    private fun likePattern(term: String): String {
        val escaped = term
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    private fun termsOf(query: String): List<String> {
        val terms = ArrayList<String>()
        val current = StringBuilder()
        for (character in query) {
            if (character.isLetterOrDigit()) {
                current.append(character)
            } else {
                if (current.isNotEmpty()) {
                    terms.add(current.toString())
                    current.setLength(0)
                }
            }
        }
        if (current.isNotEmpty()) terms.add(current.toString())
        return terms
            .filter { it.length >= MIN_LIKE_TERM }
            .distinct()
    }
}
