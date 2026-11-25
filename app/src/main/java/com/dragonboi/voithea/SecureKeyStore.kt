
package com.dragonboi.voithea

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredMailBridge(
    val webAppUrl: String,
    val apiToken: String
)

class SecureKeyStore(context: Context) {

    private val preferences = context.getSharedPreferences(
        "voithea_secure_storage",
        Context.MODE_PRIVATE
    )

    // Keep this alias unchanged to preserve existing Groq encryption.
    private val alias = "voithea_groq_key"

    private fun getOrCreateKey(): SecretKey {

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existingKey = keyStore.getKey(alias, null)

        if (existingKey != null) {
            return existingKey as SecretKey
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val specification = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        generator.init(specification)

        return generator.generateKey()
    }

    private fun saveEncrypted(
        value: String,
        ivKey: String,
        dataKey: String
    ) {

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateKey()
        )

        val encrypted = cipher.doFinal(
            value.toByteArray(Charsets.UTF_8)
        )

        val iv = Base64.encodeToString(
            cipher.iv,
            Base64.NO_WRAP
        )

        val data = Base64.encodeToString(
            encrypted,
            Base64.NO_WRAP
        )

        preferences.edit()
            .putString(ivKey, iv)
            .putString(dataKey, data)
            .apply()
    }

    private fun getDecrypted(
        ivKey: String,
        dataKey: String
    ): String? {

        val ivString = preferences.getString(ivKey, null)
            ?: return null

        val dataString = preferences.getString(dataKey, null)
            ?: return null

        return try {

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")

            val iv = Base64.decode(
                ivString,
                Base64.NO_WRAP
            )

            val encrypted = Base64.decode(
                dataString,
                Base64.NO_WRAP
            )

            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, iv)
            )

            String(
                cipher.doFinal(encrypted),
                Charsets.UTF_8
            )

        } catch (_: Exception) {
            null
        }
    }

    // =========================================
    // GROQ API KEY
    // =========================================

    fun save(apiKey: String) {

        // These are the original preference keys.
        // Existing Groq credentials remain compatible.
        saveEncrypted(
            value = apiKey,
            ivKey = "iv",
            dataKey = "data"
        )
    }

    fun get(): String? {

        return getDecrypted(
            ivKey = "iv",
            dataKey = "data"
        )
    }

    // =========================================
    // GMAIL BRIDGE CREDENTIALS
    // =========================================

    private fun getMailPrefix(accountNumber: Int): String {

        require(accountNumber in 1..2) {
            "Invalid Gmail account number."
        }

        return "gmail_$accountNumber"
    }

    fun saveMailBridge(
        accountNumber: Int,
        webAppUrl: String,
        apiToken: String
    ) {

        val prefix = getMailPrefix(accountNumber)

        saveEncrypted(
            value = webAppUrl,
            ivKey = "${prefix}_url_iv",
            dataKey = "${prefix}_url_data"
        )

        saveEncrypted(
            value = apiToken,
            ivKey = "${prefix}_token_iv",
            dataKey = "${prefix}_token_data"
        )
    }

    fun getMailBridge(accountNumber: Int): StoredMailBridge? {

        val prefix = getMailPrefix(accountNumber)

        val url = getDecrypted(
            ivKey = "${prefix}_url_iv",
            dataKey = "${prefix}_url_data"
        ) ?: return null

        val token = getDecrypted(
            ivKey = "${prefix}_token_iv",
            dataKey = "${prefix}_token_data"
        ) ?: return null

        if (url.isBlank() || token.isBlank()) {
            return null
        }

        return StoredMailBridge(
            webAppUrl = url,
            apiToken = token
        )
    }

    fun clearMailBridge(accountNumber: Int) {

        val prefix = getMailPrefix(accountNumber)

        preferences.edit()
            .remove("${prefix}_url_iv")
            .remove("${prefix}_url_data")
            .remove("${prefix}_token_iv")
            .remove("${prefix}_token_data")
            .apply()
    }

    // Clears stored values, not the Android Keystore key.
    fun clear() {
        preferences.edit().clear().apply()
    }
}
