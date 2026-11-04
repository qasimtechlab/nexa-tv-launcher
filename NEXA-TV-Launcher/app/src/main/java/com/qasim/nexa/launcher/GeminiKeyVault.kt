package com.qasim.nexa.launcher

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the user's own API key in app-private storage.
 * Android Keystore AES-GCM encryption is used on Android 6.0+.
 * Older devices use the app-private preferences fallback; backups are disabled in the manifest.
 */
internal class GeminiKeyVault(context: Context) {
    private val preferences = context.getSharedPreferences("nexa_ai_key_store", Context.MODE_PRIVATE)

    fun save(value: String) {
        val clean = value.trim()
        require(clean.isNotBlank()) { "API key cannot be empty." }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
                preferences.edit()
                    .remove(KEY_PLAIN)
                    .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .putString(KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .apply()
                return
            } catch (_: Exception) {
                // Some TV firmware images have incomplete Android Keystore implementations.
            }
        }

        preferences.edit()
            .remove(KEY_IV)
            .remove(KEY_CIPHER)
            .putString(KEY_PLAIN, clean)
            .apply()
    }

    fun read(): String? {
        val plain = preferences.getString(KEY_PLAIN, null)
        if (!plain.isNullOrBlank()) return plain

        val ivText = preferences.getString(KEY_IV, null) ?: return null
        val cipherText = preferences.getString(KEY_CIPHER, null) ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = Base64.decode(ivText, Base64.NO_WRAP)
            val encrypted = Base64.decode(cipherText, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        preferences.edit().clear().apply()
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
            // No action needed if this device does not expose Android Keystore.
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.M)
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(specification)
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "nexa_gemini_api_key_v1"
        const val KEY_IV = "key_iv"
        const val KEY_CIPHER = "key_cipher"
        const val KEY_PLAIN = "key_legacy"
    }
}
