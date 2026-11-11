/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.model.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OverridesTest {

    @Test
    fun `an account says nothing until it is given a value, and can take it back`() {
        val none = Overrides()
        val own = none.with(CallSettings.Dtmf, Override.Value(DtmfMethod.SipInfo))

        assertEquals(Override.Inherit, none[CallSettings.Dtmf])
        assertEquals(Override.Value(DtmfMethod.SipInfo), own[CallSettings.Dtmf])
        assertEquals(Override.Inherit, own[AudioSettings.Codecs])
        assertTrue(own.with(CallSettings.Dtmf, Override.Inherit).isEmpty)
    }

    @Test
    fun `the account's value applies over the app's`() {
        assertEquals(DtmfMethod.InBand, Override.Inherit.over(DtmfMethod.InBand))
        assertEquals(
            DtmfMethod.SipInfo,
            Override.Value(DtmfMethod.SipInfo).over(DtmfMethod.InBand),
        )
    }

    @Test
    fun `an option that is not overridable is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            Overrides().with(CallSettings.CallWaiting, Override.Value(false))
        }
    }

    @Test
    fun `a stored override that means nothing any more is no override`() {
        val stale = Overrides(mapOf(CallSettings.Dtmf.key to StoredValue.Str("Telepathy")))

        assertEquals(Override.Inherit, stale[CallSettings.Dtmf])
    }

    @Test
    fun `effective settings read the account, then the app`() = runTest {
        val app =
            object : SettingsStore {
                override fun <T : Any> observe(setting: Setting<T>): Flow<T> {
                    @Suppress("UNCHECKED_CAST")
                    return flowOf(
                        if (setting === CallSettings.Dtmf) DtmfMethod.InBand as T
                        else setting.default,
                    )
                }

                override suspend fun <T : Any> set(setting: Setting<T>, value: T) = Unit

                override suspend fun reset(setting: Setting<*>) = Unit
            }
        val effective = EffectiveSettings(app)
        val own = Overrides().with(CallSettings.Dtmf, Override.Value(DtmfMethod.SipInfo))

        assertEquals(DtmfMethod.InBand, effective.observe(CallSettings.Dtmf, Overrides()).first())
        assertEquals(DtmfMethod.SipInfo, effective.observe(CallSettings.Dtmf, own).first())
        assertEquals(
            AudioSettings.Codecs.default,
            effective.observe(AudioSettings.Codecs, own).first(),
        )
    }

    @Test
    fun `every kind of stored value survives being text`() {
        val values =
            listOf(
                StoredValue.Bool(true),
                StoredValue.Int32(-5),
                StoredValue.Int64(600_000),
                StoredValue.Str("opus,-gsm"),
                StoredValue.Str(""),
                StoredValue.Str("a:b"),
            )

        assertEquals(values, values.map { storedValueOf(it.toText()) })
        assertNull(storedValueOf("x:1"))
        assertNull(storedValueOf("i:many"))
        assertNull(storedValueOf("nothing"))
    }
}
