/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona

import com.bobbyesp.sona.calling.domain.Call
import com.bobbyesp.sona.calling.domain.CallState
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.sip.api.CallDirection
import com.bobbyesp.sona.sip.api.CallVideo
import com.bobbyesp.sona.sip.api.VideoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PictureInPictureTest {

    @Test
    fun `the window takes the picture's shape, within what Android accepts`() {
        assertEquals(PipAspect(1280, 720), pipAspect(VideoSize.Hd720))
        assertEquals(PipAspect(480, 640), pipAspect(VideoSize(480, 640)))
        assertEquals(PipAspect(239, 100), pipAspect(VideoSize(3000, 1000)))
        assertEquals(PipAspect(100, 239), pipAspect(VideoSize(100, 1000)))
        assertEquals(PipAspect(9, 16), pipAspect(null))
        assertEquals(PipAspect(9, 16), pipAspect(VideoSize(0, 0)))
    }

    @Test
    fun `only an answered video call goes to Picture-in-Picture`() {
        val both = CallVideo(sending = true, receiving = true, remoteSending = true)
        val hd = both.copy(receivedSize = VideoSize(720, 1280))

        assertNull(pipTarget(emptyList()))
        assertNull(pipTarget(listOf(call("a", CallState.Active, video = null))))
        assertNull(pipTarget(listOf(call("a", CallState.OnHold, both))))
        assertNull(pipTarget(listOf(call("a", CallState.Dialing, both))))

        val target = pipTarget(listOf(call("a", CallState.Active, hd, muted = true)))!!
        assertEquals(CallId("a"), target.call)
        assertEquals(PipAspect(720, 1280), target.aspect)
        assertEquals(true, target.muted)
    }

    @Test
    fun `the call whose picture arrives is preferred`() {
        val mine = CallVideo(sending = true, receiving = true, remoteSending = false)
        val theirs = CallVideo(sending = false, receiving = true, remoteSending = true)
        val calls =
            listOf(call("mine", CallState.Active, mine), call("theirs", CallState.Active, theirs))
        assertEquals(CallId("theirs"), pipTarget(calls)!!.call)
    }

    private fun call(id: String, state: CallState, video: CallVideo?, muted: Boolean = false) =
        Call(
            id = CallId(id),
            account = null,
            direction = CallDirection.Outgoing,
            remote = SipAddress("101", "lab.local"),
            remoteName = null,
            state = state,
            muted = muted,
            video = video,
        )
}
