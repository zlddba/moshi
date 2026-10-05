package dev.zlddba.moshiapp.domain.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object LockCredential {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun newSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hash(credential: String, salt: String): String {
        val spec = PBEKeySpec(
            credential.toCharArray(),
            Base64.decode(salt, Base64.NO_WRAP),
            ITERATIONS,
            KEY_BITS
        )
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            Base64.encodeToString(factory.generateSecret(spec).encoded, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(credential: String, salt: String?, expected: String?): Boolean {
        if (salt.isNullOrBlank() || expected.isNullOrBlank()) return false
        return try {
            constantTimeEquals(hash(credential, salt), expected)
        } catch (e: Throwable) {
            false
        }
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        val a = left.toByteArray(Charsets.UTF_8)
        val b = right.toByteArray(Charsets.UTF_8)
        if (a.size != b.size) return false
        var diff = 0
        for (index in a.indices) {
            diff = diff or (a[index].toInt() xor b[index].toInt())
        }
        return diff == 0
    }
}
