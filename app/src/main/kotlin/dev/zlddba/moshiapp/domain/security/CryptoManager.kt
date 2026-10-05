package dev.zlddba.moshiapp.domain.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dev.zlddba.moshiapp.data.prefs.SecurityPrefs
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    private const val TAG = "CryptoManager"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val WRAP_ALIAS = "moshi_dek_wrap"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val DEK_BYTES = 32
    private const val MAGIC = "MOSHI1"
    private const val CACHE_DIR = "secure"
    private const val BUFFER_SIZE = 8192

    @Volatile
    private var cachedDek: ByteArray? = null

    fun isEnabled(context: Context): Boolean =
        SecurityPrefs(context.applicationContext).isEncryptionEnabled()

    fun databasePassphrase(context: Context): String? = try {
        Base64.encodeToString(dek(context.applicationContext), Base64.NO_WRAP)
    } catch (e: Throwable) {
        Log.e(TAG, "database passphrase unavailable", e)
        null
    }

    fun isFileEncrypted(file: File): Boolean {
        if (!file.isFile || file.length() < MAGIC.length + IV_BYTES) return false
        return try {
            val header = ByteArray(MAGIC.length)
            val read = file.inputStream().use { it.read(header) }
            read == MAGIC.length && String(header, Charsets.US_ASCII) == MAGIC
        } catch (e: Throwable) {
            false
        }
    }

    fun encryptInto(context: Context, source: InputStream, target: File): Boolean {
        return try {
            val plain = source.use { readAll(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek(context), "AES"))
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain)
            target.outputStream().use { sink ->
                sink.write(MAGIC.toByteArray(Charsets.US_ASCII))
                sink.write(iv)
                sink.write(encrypted)
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "encrypt failed target=${target.name}", e)
            target.delete()
            false
        }
    }

    fun openForRead(context: Context, file: File): File? {
        if (!file.isFile) return null
        return if (isFileEncrypted(file)) decryptToCache(context, file) else file
    }

    fun decryptToCache(context: Context, source: File): File? {
        return try {
            val bytes = source.readBytes()
            if (bytes.size <= MAGIC.length + IV_BYTES) return null
            if (String(bytes, 0, MAGIC.length, Charsets.US_ASCII) != MAGIC) return null
            val iv = bytes.copyOfRange(MAGIC.length, MAGIC.length + IV_BYTES)
            val payload = bytes.copyOfRange(MAGIC.length + IV_BYTES, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(dek(context), "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            val plain = cipher.doFinal(payload)
            val directory = File(context.applicationContext.cacheDir, CACHE_DIR).apply { mkdirs() }
            val target = File(directory, source.name)
            target.outputStream().use { it.write(plain) }
            target
        } catch (e: Throwable) {
            Log.e(TAG, "decrypt failed source=${source.name}", e)
            null
        }
    }

    fun clearCache(context: Context) {
        try {
            File(context.applicationContext.cacheDir, CACHE_DIR).listFiles()?.forEach { it.delete() }
        } catch (e: Throwable) {
        }
    }

    fun clearSession() {
        cachedDek = null
    }

    private fun dek(context: Context): ByteArray {
        cachedDek?.let { return it }
        synchronized(this) {
            cachedDek?.let { return it }
            val loaded = loadOrCreateDek(context.applicationContext)
                ?: throw IllegalStateException("dek unavailable")
            cachedDek = loaded
            return loaded
        }
    }

    private fun loadOrCreateDek(context: Context): ByteArray? {
        val prefs = SecurityPrefs(context)
        val stored = prefs.wrappedDek()
        if (!stored.isNullOrBlank()) {
            val unwrapped = unwrap(stored)
            if (unwrapped == null) {
                Log.e(TAG, "stored dek cannot be unwrapped, refusing to rotate")
                return null
            }
            return unwrapped
        }
        val fresh = ByteArray(DEK_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapped = wrap(fresh) ?: return null
        prefs.setWrappedDek(wrapped)
        Log.i(TAG, "dek generated and wrapped")
        return fresh
    }

    private fun wrap(plain: ByteArray): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey())
        val payload = cipher.iv + cipher.doFinal(plain)
        Base64.encodeToString(payload, Base64.NO_WRAP)
    } catch (e: Throwable) {
        Log.e(TAG, "wrap failed", e)
        null
    }

    private fun unwrap(value: String): ByteArray? = try {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        if (payload.size <= IV_BYTES) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                wrapKey(),
                GCMParameterSpec(GCM_TAG_BITS, payload.copyOfRange(0, IV_BYTES))
            )
            cipher.doFinal(payload.copyOfRange(IV_BYTES, payload.size))
        }
    } catch (e: Throwable) {
        Log.e(TAG, "unwrap failed", e)
        null
    }

    private fun wrapKey(): SecretKey {
        existingWrapKey()?.let { return it }
        synchronized(this) {
            existingWrapKey()?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    WRAP_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return generator.generateKey()
        }
    }

    private fun existingWrapKey(): SecretKey? = try {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(WRAP_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (e: Throwable) {
        null
    }

    private fun readAll(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }
}
