package `in`.vidyadhan.academy.lock

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/** Stores the app PIN as a salted hash (never the PIN itself). */
class LockStore(context: Context) {
    private val prefs = context.getSharedPreferences("lock", Context.MODE_PRIVATE)

    val hasPin: Boolean get() = prefs.contains("hash")
    var biometricOn: Boolean
        get() = prefs.getBoolean("bio", true)
        set(v) = prefs.edit().putBoolean("bio", v).apply()

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("salt", salt.toHex())
            .putString("hash", hash(pin, salt).toHex())
            .apply()
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString("salt", null)?.hexToBytes() ?: return false
        val expected = prefs.getString("hash", null) ?: return false
        return MessageDigest.isEqual(hash(pin, salt).toHex().toByteArray(), expected.toByteArray())
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        var d = salt + pin.toByteArray()
        repeat(10_000) { d = md.digest(d) }
        return d
    }
}

internal fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
internal fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
