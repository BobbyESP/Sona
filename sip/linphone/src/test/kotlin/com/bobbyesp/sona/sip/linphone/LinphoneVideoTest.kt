/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.sip.api.CallVideo
import com.bobbyesp.sona.sip.api.CameraFacing
import com.bobbyesp.sona.sip.api.VideoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.linphone.core.Call.State as Linphone
import org.linphone.core.CallParams
import org.linphone.core.MediaDirection
import org.linphone.core.PayloadType
import org.linphone.core.VideoDefinition

class LinphoneVideoTest {

    private fun definition(width: Int, height: Int): VideoDefinition =
        stand("getWidth" to width, "getHeight" to height, "isUndefined" to false)

    private fun params(
        video: Boolean = true,
        direction: MediaDirection = MediaDirection.SendRecv,
        sent: VideoDefinition? = null,
        received: VideoDefinition? = null,
        codec: String? = null,
    ): CallParams =
        stand(
            "isVideoEnabled" to video,
            "getVideoDirection" to direction,
            "getSentVideoDefinition" to sent,
            "getReceivedVideoDefinition" to received,
            "getUsedVideoPayloadType" to codec?.let { stand<PayloadType>("getMimeType" to it) },
        )

    @Test
    fun `the preview runs behind a ringing call, without the camera sent, once a view is attached`() {
        assertTrue(previewRuns(attached = true, callGoing = true, cameraAllowed = true))
        assertFalse(previewRuns(attached = false, callGoing = true, cameraAllowed = true))
        assertFalse(previewRuns(attached = true, callGoing = true, cameraAllowed = false))
        assertFalse(previewRuns(attached = true, callGoing = false, cameraAllowed = true))
    }

    @Test
    fun `directions are what each side sends and receives`() {
        assertEquals(MediaDirection.SendRecv, videoDirectionOf(send = true, receive = true))
        assertEquals(MediaDirection.SendOnly, videoDirectionOf(send = true, receive = false))
        assertEquals(MediaDirection.RecvOnly, videoDirectionOf(send = false, receive = true))
        assertNull(videoDirectionOf(send = false, receive = false))
    }

    @Test
    fun `an answer takes offered video both ways with the camera, receive-only without`() {
        val table =
            listOf(
                listOf(true, true, true, true) to MediaDirection.SendRecv,
                listOf(true, false, true, true) to MediaDirection.RecvOnly,
                listOf(true, false, true, false) to null,
                // Nothing offered: an answer cannot add video.
                listOf(true, true, false, true) to null,
                // Video off: never.
                listOf(false, true, true, true) to null,
            )
        table.forEach { (given, expected) ->
            val (enabled, camera, offered, accept) = given
            assertEquals("$given", expected, answerDirectionOf(enabled, camera, offered, accept))
        }
    }

    @Test
    fun `a call's video says who sends, who receives, and what`() {
        val video =
            callVideoOf(
                local =
                    params(
                        direction = MediaDirection.SendRecv,
                        sent = definition(720, 1280),
                        received = definition(1280, 720),
                        codec = "VP8",
                    ),
                remote = params(direction = MediaDirection.RecvOnly),
                camera = CameraFacing.Back,
            )

        assertEquals(
            CallVideo(
                sending = true,
                receiving = true,
                remoteSending = false,
                camera = CameraFacing.Back,
                sentSize = VideoSize(720, 1280),
                receivedSize = VideoSize(1280, 720),
                codec = "VP8",
            ),
            video,
        )
    }

    @Test
    fun `receive-only video shows the other side and sends nothing`() {
        val video =
            callVideoOf(
                local = params(direction = MediaDirection.RecvOnly, sent = definition(640, 480)),
                remote = params(direction = MediaDirection.SendOnly),
                camera = CameraFacing.Front,
            )!!

        assertFalse(video.sending)
        assertTrue(video.showsRemote)
        // Nothing is sent: no size to show for it.
        assertNull(video.sentSize)
    }

    @Test
    fun `no video stream is no video, and the other side's is unknown before its answer`() {
        assertNull(callVideoOf(params(video = false), null, CameraFacing.Front))
        val asked = callVideoOf(params(), remote = null, CameraFacing.Front)!!
        assertTrue(asked.sending)
        assertFalse(asked.remoteSending)
        // The other side turned video down: its parameters have none.
        val declined = callVideoOf(params(), params(video = false), CameraFacing.Front)!!
        assertFalse(declined.remoteSending)
    }

    @Test
    fun `a definition not known yet is no size`() {
        val undefined: VideoDefinition = stand("isUndefined" to true)
        assertNull(undefined.toSize())
        assertNull(definition(0, 0).toSize())
        assertNull((null as VideoDefinition?).toSize())
        assertEquals(VideoSize(320, 240), definition(320, 240).toSize())
    }

    @Test
    fun `media are negotiated once the call is answered or has early media`() {
        listOf(Linphone.OutgoingInit, Linphone.OutgoingProgress, Linphone.OutgoingRinging).forEach {
            assertFalse("$it", it.negotiated())
        }
        listOf(Linphone.OutgoingEarlyMedia, Linphone.StreamsRunning, Linphone.Paused).forEach {
            assertTrue("$it", it.negotiated())
        }
        assertTrue(Linphone.IncomingReceived.ringingHere())
        assertFalse(Linphone.StreamsRunning.ringingHere())
    }

    private val phone = listOf("Android0 (Back)", "Android1 (Front)", "StaticImage: Static picture")

    @Test
    fun `a camera faces the way its name says, and the still picture is none`() {
        assertEquals(CameraFacing.Front, cameraFacingOf("Android1 (Front)"))
        assertEquals(CameraFacing.Back, cameraFacingOf("Android0 (Back)"))
        assertEquals(CameraFacing.Back, cameraFacingOf("Rear wide"))
        assertNull(cameraFacingOf("StaticImage: Static picture"))
        assertNull(cameraFacingOf("USB camera"))
        assertNull(cameraFacingOf(null))
    }

    @Test
    fun `the front camera is found, and a phone that does not say gets its first camera`() {
        assertEquals("Android1 (Front)", cameraFor(phone, CameraFacing.Front))
        assertEquals("Android0 (Back)", cameraFor(phone, CameraFacing.Back))
        assertEquals(
            "USB camera",
            cameraFor(listOf("StaticImage", "USB camera"), CameraFacing.Front),
        )
        assertNull(cameraFor(listOf("StaticImage: Static picture"), CameraFacing.Front))
    }

    @Test
    fun `switching takes the camera facing the other way, skipping the still picture`() {
        assertEquals("Android0 (Back)", otherCamera(phone, "Android1 (Front)"))
        assertEquals("Android1 (Front)", otherCamera(phone, "Android0 (Back)"))
        // Names that do not say: the next camera, round.
        val unnamed = listOf("cam0", "cam1", "StaticImage: Static picture", "cam2")
        assertEquals("cam1", otherCamera(unnamed, "cam0"))
        assertEquals("cam0", otherCamera(unnamed, "cam2"))
        assertEquals("cam0", otherCamera(unnamed, "StaticImage: Static picture"))
        assertNull(otherCamera(listOf("cam0", "StaticImage: Static picture"), "cam0"))
    }
}
