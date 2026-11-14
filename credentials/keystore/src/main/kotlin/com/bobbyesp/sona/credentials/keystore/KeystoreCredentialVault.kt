/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.credentials.keystore

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.credentials.api.CredentialVault
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The app's password vault. Keystore and its files stay implementation details. */
fun Context.createCredentialVault(): CredentialVault = KeystoreCredentialVault(applicationContext)

/**
 * Passwords sealed with AES-GCM under a key that lives in the Android Keystore and never leaves it.
 * What is stored on disk is the IV and the ciphertext; without the key, which a backup does not
 * carry, they open nothing.
 *
 * Not Jetpack Security's `EncryptedSharedPreferences`: it is deprecated, and this is all of it that
 * Sona needs.
 */
internal class KeystoreCredentialVault(context: Context) : CredentialVault {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val lock = Mutex()

    override suspend fun store(account: AccountId, secret: Secret) = lock.withLock {
        withContext(Dispatchers.IO) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val sealed = cipher.doFinal(secret.reveal().toByteArray(Charsets.UTF_8))
            val value = cipher.iv.encode() + SEPARATOR + sealed.encode()
            prefs.edit().putString(account.value, value).apply()
        }
    }

    override suspend fun read(account: AccountId): Secret? = lock.withLock {
        withContext(Dispatchers.IO) {
            val value = prefs.getString(account.value, null) ?: return@withContext null
            val iv = value.substringBefore(SEPARATOR).decode()
            val sealed = value.substringAfter(SEPARATOR).decode()
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
                Secret(String(cipher.doFinal(sealed), Charsets.UTF_8))
            } catch (_: GeneralSecurityException) {
                // The key that sealed it is gone: the password has to be typed again.
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    override suspend fun remove(account: AccountId) = lock.withLock {
        withContext(Dispatchers.IO) {
            prefs.edit().remove(account.value).apply()
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
            return it
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val FILE_NAME = "sona_credentials"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sona.sip.credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val SEPARATOR = ':'
    }
}
