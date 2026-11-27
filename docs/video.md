# Video calls

How a call carries video: what the engine does, what the coordinator decides, how the network is
handled, and what the call screen shows. Audio rules ([calling.md](calling.md)) all still apply:
video is a second stream on the same call, never a second kind of call.

```
 call screen ── intents ──> CallCoordinator ── SipEngine (video primitives) ──> Linphone (Core thread)
     │                         │  decisions: answer with/without camera,            │
     │                         │  upgrade/downgrade, speaker for video,             │
     │                         │  camera foreground type, privacy on accept         │
     │                         └─ SystemCalls (Telecom: video call type)            │
     │                                                                              │
     └── VideoSurfaces (TextureViews, as opaque handles) ──────────────────────────┘
         CallStatistics flow (1 Hz while observed) <────────────────────────────────┘
```

## Contract (`:sip:api`)

- `Capability.VideoCalls`. The fake engine has it; the Linphone engine has it once it runs on the
  `org.linphone:linphone-sdk-android` artifact (the video one; `no-video` is dropped).
- `EngineCall.video: CallVideo?`: `null` for an audio-only call. It says whether video is
  negotiated, whether this side sends (camera on) and receives, whether the remote sends, which
  camera is in use (`CameraFacing.Front`/`Back`), and the sizes sent and received (for the
  screen's aspect ratio and Picture-in-Picture).
- Primitives, only called by the coordinator: `dial(account, to, video)`, `answer(call, video)`,
  `setVideo(call, send, receive)` (a re-INVITE with the new directions), `switchCamera(call)`,
  `setDeviceRotation(degrees)`.
- `statistics(call): Flow<CallStatistics>`: polled on the Core thread once a second **only while
  someone collects it** (the stats panel, the quality badge). Nothing is computed when nobody
  looks. `CallStatistics` holds per stream (audio, video): codec and clock rate, bitrate up and
  down (kbit/s), packets sent/received, cumulative loss, local and remote loss rate, jitter, round
  trip, jitter buffer, late packets, FEC repaired packets, estimated download bandwidth; for video
  also definition and frame rate sent and received; for the call: SIP transport (UDP/TCP/TLS), ICE
  state, IP family, media encryption and SRTP suite.
- `VideoSurfaces`, a port of its own: `attach(call, remote: Any?, preview: Any?)`. On Android the
  handles are `TextureView`s; they travel as `Any` so `:sip:api` stays plain Kotlin. The full
  flavor binds the Linphone engine; the demo flavor binds a renderer that paints a moving test
  pattern, so the demo shows the whole video screen without a camera or a server.

## Decisions (`CallCoordinator`, each with a scenario test)

| Situation | Decision |
|---|---|
| Dial with video | Telecom `CALL_TYPE_VIDEO_CALL`; camera on; speaker unless a headset or Bluetooth is the route |
| Incoming offer with video | Rings as a video call; *Answer with video*, *Answer audio only*, *Decline* |
| Answer audio only | Video stays negotiated receive-only when the remote offered it, camera off; the user can turn it on |
| The remote adds video mid-call | Accepted **receive-only**: the remote is seen, this side's camera stays off until the user turns it on (privacy). The screen offers *Turn on camera* |
| The user turns the camera on/off | `setVideo(send = on)`; the foreground service gains or loses the `camera` type |
| Video becomes active on the earpiece | The route moves to the speaker; a headset or Bluetooth route is kept |
| Video ends (both directions off) | The route is left as it is; proximity lock is allowed again |
| Hold | Video stops with the audio; resume restores the directions held before |
| Camera permission refused | The call goes on as audio; the screen says why the camera is off |

The proximity sensor never turns the screen off during a video call, and the screen stays on.

## The network

The goal is a call that degrades gracefully instead of freezing. All of it is engine
configuration (`EngineConfig.video`, applied in `LinphoneConfigurer`), from settings in
*Settings › Video*:

- **Adaptive rate control** on, with Linphone's `advanced` algorithm: it reads RTCP (loss, delay
  trend, TMMBR) and lowers bitrate, then frame rate, then definition when the path is congested,
  and raises them again when it clears.
- **Losses are repaired, not hidden**: retransmission on NACK and video FEC (both negotiated; a
  server or peer that does not support them simply answers without them), RTCP feedback (PLI/FIR)
  so a lost key frame is asked for again instead of showing a smeared picture for seconds.
- **Adaptive jitter buffers** for audio and video, so a jittery Wi-Fi does not drop frames.
- **Bandwidth by network**: on Wi-Fi the preferred definition and upload cap from the settings
  (720p, 1.5 Mbit/s by default); on mobile data a lower cap (VGA, 512 kbit/s by default) and
  Linphone's low-bandwidth mode. `NetworkMonitor` (`:calling:platform`) says which network is in
  use, and the `EngineConfig` is rebuilt when it changes.
- **Priority marking**: DSCP EF (46) for audio and AF41 (34) for video, so routers that honour it
  keep voice first.
- **Audio first**: when the link is very poor, video is what is lowered or dropped, never the voice.

What the user sees of it:

- A **quality indicator** (bars) from `CallQuality.of(statistics)`, a pure function with table
  tests: loss, jitter and round trip turn into Good, Fair or Poor.
- After several seconds of *Poor* in a video call, a banner: "Weak connection: video lowered",
  with *Turn off video* to keep the voice.
- **Frozen video**: when the remote should send but no frame arrived for 3 seconds, the last frame
  dims under "Video paused".

## Cameras

- Front by default; *Switch camera* swaps front and back without renegotiating.
- The front preview is mirrored on screen (never in what is sent).
- `Core.deviceRotation` follows the display rotation, so the picture sent is upright.
- Camera off is a direction change (`sendonly`→`recvonly`, `sendrecv`→`recvonly`): nothing is
  sent, which saves upload, and the other side shows its placeholder.

## The call screen

- **Video layout**: the remote picture fills the screen (fit or fill, double tap); the local
  preview is a floating tile that snaps to the nearest corner when dragged, and swaps with the
  remote on tap. Controls fade after a few seconds and come back on a tap.
- **Controls** on a frosted bar (`:core:designsystem` blur): mute, camera on/off, switch camera,
  audio route, more (statistics, keypad, hold), and hang up.
- **Statistics**: a frosted panel, updated every second, with the numbers above and small
  sparklines of bitrate and frame rate for the last minute. Opened from *More* or the quality
  indicator. On a wide window it is a side panel instead of an overlay.
- **Responsive**: portrait, landscape (controls on a rail at the side) and wide windows; nothing
  measures the window, it reads the pane context.
- **Picture-in-Picture**: leaving a video call shrinks it to a PiP window with the remote picture
  in its aspect ratio and *Mute*/*Hang up* actions.

## Asterisk

Video passes through Asterisk when the endpoints allow a common video codec: `allow=...,vp8,h264`
and `max_video_streams=1` on both endpoints (the `lab/` configuration has it). Linphone offers
VP8, H.264 and H.265 (hardware through MediaCodec); the codec order is a setting like the audio
one.
