/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.credentials.keystore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Only a device has a Keystore, so the vault is tested on one. */
@RunWith(AndroidJUnit4::class)
class KeystoreCredentialVaultTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val vault = KeystoreCredentialVault(context)
    private val account = AccountId("test-account")

    @Test
    fun aPasswordReadsBackAsItWasStored() = runTest {
        vault.store(account, Secret("pässwörd 102"))

        assertEquals("pässwörd 102", vault.read(account)?.reveal())
        vault.remove(account)
    }

    @Test
    fun whatIsOnDiskIsNotThePassword() = runTest {
        vault.store(account, Secret("hunter2"))

        val onDisk =
            context.getSharedPreferences("sona_credentials", Context.MODE_PRIVATE).all.values
        assertFalse(onDisk.any { it.toString().contains("hunter2") })
        vault.remove(account)
    }

    @Test
    fun aRemovedPasswordIsGone() = runTest {
        vault.store(account, Secret("x"))
        vault.remove(account)

        assertNull(vault.read(account))
    }
}
