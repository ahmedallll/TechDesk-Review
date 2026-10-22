package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.data.model.DeviceCredentials
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed encrypted storage for device credentials.
 * Fails safely: never stores tokens in plaintext or unencrypted fallback.
 */
class SecureStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SecureStorage"
        private const val PREFS_NAME = "techdesk_secure_prefs"
        private const val KEY_ENCRYPTED_DATA = "enc_credentials"
        private const val KEY_ALIAS = "techdesk_keystore_alias"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(parameterSpec)
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw SecurityException("Android KeyStore entry for $KEY_ALIAS is not a SecretKeyEntry")
        return entry.secretKey
    }

    /**
     * Encrypts plaintext using hardware-backed AES-GCM.
     * Fails closed: throws SecurityException rather than saving unencrypted credentials.
     */
    private fun encrypt(plainText: String): String {
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv ?: throw SecurityException("Cipher failed to generate initialization vector (IV)")
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val buffer = ByteBuffer.allocate(4 + iv.size + cipherBytes.size)
            buffer.putInt(iv.size)
            buffer.put(iv)
            buffer.put(cipherBytes)
            return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "KeyStore encryption failed - credentials will NOT be saved in plain text", e)
            throw SecurityException("Secure KeyStore encryption failed: ${e.message}", e)
        }
    }

    /**
     * Decrypts ciphertext using hardware-backed AES-GCM.
     * Rejects invalid, truncated, or unauthenticated payloads.
     */
    private fun decrypt(encryptedBase64: String): String {
        try {
            val decoded = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(decoded)
            if (buffer.remaining() <= 4) {
                throw SecurityException("Corrupted encrypted payload: buffer too short")
            }
            val ivSize = buffer.int
            if (ivSize !in 12..16 || buffer.remaining() <= ivSize) {
                throw SecurityException("Corrupted encrypted payload: invalid IV size $ivSize")
            }
            val iv = ByteArray(ivSize)
            buffer.get(iv)
            val cipherBytes = ByteArray(buffer.remaining())
            buffer.get(cipherBytes)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainBytes = cipher.doFinal(cipherBytes)
            return String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "KeyStore decryption failed", e)
            throw SecurityException("Secure KeyStore decryption failed: ${e.message}", e)
        }
    }

    fun saveCredentials(credentials: DeviceCredentials) {
        val json = JSONObject().apply {
            put("installationReference", credentials.installationReference)
            put("host", credentials.host)
            put("port", credentials.port)
            put("deviceId", credentials.deviceId)
            put("deviceToken", credentials.deviceToken)
            put("protocolVersion", credentials.protocolVersion)
            put("deviceName", credentials.deviceName)
        }.toString()

        val encrypted = encrypt(json)
        prefs.edit().putString(KEY_ENCRYPTED_DATA, encrypted).apply()
    }

    fun getCredentials(): DeviceCredentials? {
        val encrypted = prefs.getString(KEY_ENCRYPTED_DATA, null) ?: return null
        return try {
            val decrypted = decrypt(encrypted)
            if (decrypted.isBlank()) return null
            val json = JSONObject(decrypted)
            DeviceCredentials(
                installationReference = json.getString("installationReference"),
                host = json.getString("host"),
                port = json.getInt("port"),
                deviceId = json.getString("deviceId"),
                deviceToken = json.getString("deviceToken"),
                protocolVersion = json.optString("protocolVersion", "1.0.0"),
                deviceName = json.optString("deviceName", "Android Scanner")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read credentials securely", e)
            null
        }
    }

    fun updateHostAndPort(host: String, port: Int) {
        val current = getCredentials() ?: return
        saveCredentials(current.copy(host = host, port = port))
    }

    fun clearCredentials() {
        prefs.edit().remove(KEY_ENCRYPTED_DATA).apply()
    }

    fun hasCredentials(): Boolean {
        return prefs.contains(KEY_ENCRYPTED_DATA)
    }
}
