package com.dudeapp.vault

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom

/** PIN stored as a salted SHA-256 hash. This is an app lock, not file encryption. */
object Auth {
    private const val PREFS = "dude_auth"
    private const val KEY_SALT = "salt"
    private const val KEY_HASH = "hash"
    private const val KEY_FAILED = "failed"
    private const val KEY_LOCK_UNTIL = "lock_until"
    private const val MAX_FAILURES = 5
    private const val LOCKOUT_MS = 30_000L

    fun hasPin(context: Context): Boolean = prefs(context).contains(KEY_HASH)

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(context).edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .remove(KEY_FAILED)
            .remove(KEY_LOCK_UNTIL)
            .apply()
    }

    fun checkPin(context: Context, pin: String): Boolean {
        if (lockoutUntil(context) > System.currentTimeMillis()) return false
        val prefs = prefs(context)
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        val actual = Base64.encodeToString(hash(pin, Base64.decode(salt, Base64.NO_WRAP)), Base64.NO_WRAP)
        val matched = MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray())
        if (matched) clearFailures(context)
        return matched
    }

    fun lockoutUntil(context: Context): Long = prefs(context).getLong(KEY_LOCK_UNTIL, 0L)

    fun registerFailure(context: Context): Long {
        val prefs = prefs(context)
        val failures = prefs.getInt(KEY_FAILED, 0) + 1
        val locked = failures >= MAX_FAILURES
        val until = if (locked) System.currentTimeMillis() + LOCKOUT_MS else 0L
        prefs.edit()
            .putInt(KEY_FAILED, if (locked) 0 else failures)
            .putLong(KEY_LOCK_UNTIL, until)
            .apply()
        return until
    }

    fun clearFailures(context: Context) {
        prefs(context).edit()
            .remove(KEY_FAILED)
            .remove(KEY_LOCK_UNTIL)
            .apply()
    }

    fun biometricsAvailable(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun promptBiometric(activity: FragmentActivity, onSuccess: () -> Unit, onFail: (String) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onFail("")
                    } else {
                        onFail(errString.toString())
                    }
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock P Vault")
                .setSubtitle("Use your fingerprint")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build(),
        )
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .apply {
                update(salt)
                update(pin.toByteArray())
            }
            .digest()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
