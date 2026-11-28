/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call.video

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.bobbyesp.sona.sip.api.VideoSize
import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {
    private val area = Size(400f, 800f)
    private val tile = Size(100f, 140f)

    @Test
    fun `each corner rests the tile its margin from the edges`() {
        assertEquals(Offset(10f, 10f), cornerOffset(Corner.TopStart, tile, area, 10f))
        assertEquals(Offset(290f, 10f), cornerOffset(Corner.TopEnd, tile, area, 10f))
        assertEquals(Offset(10f, 650f), cornerOffset(Corner.BottomStart, tile, area, 10f))
        assertEquals(Offset(290f, 650f), cornerOffset(Corner.BottomEnd, tile, area, 10f))
    }

    @Test
    fun `right to left, the start is on the right`() {
        assertEquals(Offset(290f, 10f), cornerOffset(Corner.TopStart, tile, area, 10f, rtl = true))
        assertEquals(Offset(10f, 650f), cornerOffset(Corner.BottomEnd, tile, area, 10f, rtl = true))
    }

    @Test
    fun `a tile let go snaps to the corner its centre is nearest`() {
        assertEquals(Corner.TopStart, nearestCorner(Offset(40f, 200f), tile, area))
        assertEquals(Corner.TopEnd, nearestCorner(Offset(200f, 100f), tile, area))
        assertEquals(Corner.BottomStart, nearestCorner(Offset(0f, 500f), tile, area))
        assertEquals(Corner.BottomEnd, nearestCorner(Offset(260f, 600f), tile, area))
        assertEquals(Corner.TopEnd, nearestCorner(Offset(40f, 200f), tile, area, rtl = true))
    }

    @Test
    fun `a fling carries the tile further than where it was let go`() {
        val leftOfCentre = Offset(100f, 100f)
        assertEquals(Corner.TopStart, nearestCorner(leftOfCentre, tile, area))
        assertEquals(
            Corner.TopEnd,
            nearestCorner(leftOfCentre, tile, area, velocity = Offset(2000f, 0f)),
        )
        assertEquals(
            Corner.BottomStart,
            nearestCorner(leftOfCentre, tile, area, velocity = Offset(0f, 3000f)),
        )
    }

    @Test
    fun `a picture fits whole or fills, at its own aspect`() {
        val landscape = Size(1000f, 500f)
        val hd = VideoSize(1280, 720)
        assertEquals(888.89f, videoFrame(landscape, hd, fill = false).width, 0.01f)
        assertEquals(500f, videoFrame(landscape, hd, fill = false).height, 0.01f)
        assertEquals(1000f, videoFrame(landscape, hd, fill = true).width, 0.01f)
        assertEquals(562.5f, videoFrame(landscape, hd, fill = true).height, 0.01f)

        val portrait = Size(400f, 800f)
        assertEquals(400f, videoFrame(portrait, hd, fill = false).width, 0.01f)
        assertEquals(225f, videoFrame(portrait, hd, fill = false).height, 0.01f)
        assertEquals(1422.22f, videoFrame(portrait, hd, fill = true).width, 0.01f)
        assertEquals(800f, videoFrame(portrait, hd, fill = true).height, 0.01f)
    }

    @Test
    fun `an unknown picture takes the space as it is`() {
        assertEquals(area, videoFrame(area, null, fill = false))
        assertEquals(area, videoFrame(area, VideoSize(0, 0), fill = true))
    }
}
