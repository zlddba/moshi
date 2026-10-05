package dev.zlddba.moshiapp.data.db

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import net.zetetic.database.sqlcipher.SQLiteDatabase

object DatabaseCipher {

    private const val TAG = "DatabaseCipher"
    private const val PLAINTEXT_HEADER = "SQLite format 3"
    private const val MIGRATING_SUFFIX = ".migrating"
    private const val EMPTY_KEY = ""
    private val SIDECAR_SUFFIXES = listOf("-journal", "-wal", "-shm")

    @Volatile
    private var nativeReady = false

    fun ensureNative(): Boolean {
        if (nativeReady) return true
        synchronized(this) {
            if (nativeReady) return true
            nativeReady = try {
                System.loadLibrary("sqlcipher")
                true
            } catch (e: Throwable) {
                Log.e(TAG, "sqlcipher native library unavailable", e)
                false
            }
            return nativeReady
        }
    }

    fun isPlaintext(file: File): Boolean {
        if (!file.isFile || file.length() < PLAINTEXT_HEADER.length) return false
        return try {
            val header = ByteArray(PLAINTEXT_HEADER.length)
            RandomAccessFile(file, "r").use { it.readFully(header) }
            String(header, Charsets.US_ASCII) == PLAINTEXT_HEADER
        } catch (e: Throwable) {
            false
        }
    }

    fun isEncryptedOnDisk(file: File): Boolean = file.isFile && !isPlaintext(file)

    fun prepare(databaseFile: File, encrypt: Boolean, passphrase: String?): Boolean {
        if (!ensureNative()) return false
        if (!databaseFile.isFile) return true
        val plaintext = isPlaintext(databaseFile)
        return when {
            encrypt && plaintext -> {
                if (passphrase.isNullOrBlank()) {
                    Log.e(TAG, "cannot encrypt database without passphrase")
                    false
                } else {
                    convert(databaseFile, EMPTY_KEY, passphrase, encrypting = true)
                }
            }

            !encrypt && !plaintext -> {
                if (passphrase.isNullOrBlank()) {
                    Log.e(TAG, "cannot decrypt database without passphrase")
                    false
                } else {
                    convert(databaseFile, passphrase, EMPTY_KEY, encrypting = false)
                }
            }

            else -> true
        }
    }

    private fun convert(
        source: File,
        sourceKey: String,
        targetKey: String,
        encrypting: Boolean
    ): Boolean {
        val temporary = File(source.parentFile, source.name + MIGRATING_SUFFIX)
        removeFile(temporary)
        removeSidecars(temporary)
        return try {
            val database = SQLiteDatabase.openOrCreateDatabase(
                source,
                sourceKey,
                null,
                null,
                null
            )
            try {
                database.rawExecSQL(
                    "ATTACH DATABASE '${escape(temporary.absolutePath)}' AS migrated " +
                        "KEY '${escape(targetKey)}'"
                )
                database.rawExecSQL("SELECT sqlcipher_export('migrated')")
                database.rawExecSQL("DETACH DATABASE migrated")
            } finally {
                database.close()
            }
            if (!temporary.isFile || temporary.length() <= 0L) {
                Log.e(TAG, "migration produced no output file=${source.name}")
                removeFile(temporary)
                return false
            }
            if (!verify(temporary, targetKey.toByteArray(Charsets.UTF_8))) {
                Log.e(TAG, "migrated database failed verification file=${source.name}")
                removeFile(temporary)
                return false
            }
            if (!source.delete()) {
                Log.e(TAG, "cannot replace source file=${source.name}")
                removeFile(temporary)
                return false
            }
            removeSidecars(source)
            if (!temporary.renameTo(source)) {
                Log.e(TAG, "cannot rename migrated file=${source.name}")
                return false
            }
            Log.i(TAG, "database migrated encrypting=$encrypting file=${source.name}")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "database migration failed file=${source.name}", e)
            removeFile(temporary)
            false
        }
    }

    private fun verify(file: File, key: ByteArray): Boolean = try {
        val database = SQLiteDatabase.openOrCreateDatabase(file, key, null, null, null)
        try {
            database.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                cursor.moveToFirst()
            }
        } finally {
            database.close()
        }
        true
    } catch (e: Throwable) {
        Log.e(TAG, "verify failed file=${file.name}", e)
        false
    }

    private fun removeSidecars(file: File) {
        SIDECAR_SUFFIXES.forEach { suffix ->
            removeFile(File(file.parentFile, file.name + suffix))
        }
    }

    private fun removeFile(file: File) {
        try {
            if (file.exists()) file.delete()
        } catch (e: Throwable) {
        }
    }

    private fun escape(value: String): String = value.replace("'", "''")
}
