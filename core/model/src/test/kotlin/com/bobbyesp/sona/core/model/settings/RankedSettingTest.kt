/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.model.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankedSettingTest {
    private val setting =
        Setting.Ranked(
            "r",
            Ranking(
                listOf(
                    Ranking.Entry("a"),
                    Ranking.Entry("b"),
                    Ranking.Entry("c", enabled = false),
                ),
            ),
        )

    private fun read(text: String) =
        setting.read(StoredValue.Str(text)).entries.joinToString(",") {
            if (it.enabled) it.id else "-${it.id}"
        }

    @Test
    fun `it reads back the order and what is off`() {
        val value = setting.default.moved("c", up = true).switched("a", enabled = false)

        assertEquals(StoredValue.Str("-a,-c,b"), setting.encode(value))
        assertEquals(value, setting.read(setting.encode(value)))
        assertEquals(listOf("b"), value.enabled)
    }

    @Test
    fun `a name no longer known is dropped, and one added since takes its default at the end`() {
        assertEquals("b,a,-c", read("b,gone,a"))
        assertEquals("c,a,b", read("c"))
        assertEquals("a,b,-c", read(""))
    }

    @Test
    fun `a name stored twice counts once, and another kind of value is the default`() {
        assertEquals("b,a,-c", read("b,-b,a"))
        assertNull(setting.decode(StoredValue.Bool(true)))
        assertEquals(setting.default, setting.read(StoredValue.Int32(3)))
    }

    @Test
    fun `moving past an end changes nothing`() {
        assertEquals(setting.default, setting.default.moved("a", up = true))
        assertEquals(setting.default, setting.default.moved("c", up = false))
        assertEquals(setting.default, setting.default.moved("nobody", up = true))
        assertEquals(
            listOf("b", "a", "c"),
            setting.default.moved("a", up = false).entries.map { it.id },
        )
    }

    @Test
    fun `only a ranking of exactly its names is accepted`() {
        assertTrue(setting.accepts(setting.default.moved("b", up = true)))
        assertFalse(setting.accepts(Ranking(listOf(Ranking.Entry("a"), Ranking.Entry("b")))))
        assertFalse(setting.accepts(Ranking(setting.default.entries + Ranking.Entry("d"))))
    }

    @Test
    fun `the codecs default to the catalogue's four, in its order`() {
        assertEquals(listOf("Opus", "G722", "Pcmu", "Pcma"), AudioSettings.Codecs.default.enabled)
    }
}
