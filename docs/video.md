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

- `Capability.VideoCalls` (and `Capability.CallStats`): both engines have them. Linphone runs on
  the `org.linphone:linphone-sdk-android` artifact, the video one.
- `EngineCall.video: CallVideo?`: `null` for a call without a video stream. `CallVideo` has
  `sending` (camera on), `receiving`, `remoteSending`, `camera` (`CameraFacing.Front`/`Back`),
  `sentSize`/`receivedSize` (`VideoSize`, for the aspect ratio and Picture-in-Picture; refreshed
  from Linphone's own stats reports), `codec`, and `showsRemote` / `isActive`.
  `EngineCall.videoOffered` says an incoming INVITE offered video.
- Primitives, only called by the coordinator: `dial(account, to, video)`, `answer(call, video)`,
  `setVideo(call, send, receive)` (a re-INVITE; both `false` drops the stream), `switchCamera(call)`,
  `setDeviceRotation(degrees)`.
- `statistics(call): Flow<CallStatistics>`: read on the Core thread once a second **only while
  collected**, ending with the call. `CallStatistics` has `audio` and `video` `StreamStatistics`
  (codec, clock rate, kbit/s up and down, packets, cumulative loss, local and remote loss %, jitter
  and remote jitter ms, round trip ms, jitter buffer, late packets, FEC repaired, estimated
  download; for video `frames`: sizes and fps sent and received), and `transport`, `ice`,
  `ipFamily`, `encryption`, `srtpSuite`. Linphone gives jitter and round trip in seconds; the
  mapper converts.
- `VideoSurfaces.attach(call, remote: Any?, preview: Any?)`, a port of its own, callable from any
  thread. On Android the handles are `TextureView`s (anything else is taken as none). The full
  flavor binds the Linphone engine (`SipEngine.linphoneVideoSurfaces()`): the remote view is the
  call's native window, the preview the Core's, and the preview runs only while a preview view is
  attached and the camera is sent; an ended call lets go of both. The demo flavor binds
  `DemoVideoSurfaces`, which paints colour bars (remote) and stripes (preview) from a background
  thread, only while a view is attached.
- `EngineConfig.video: VideoConfig`: enabled, codecs, definition, max frame rate, upload and
  download kbit/s, adaptive rate, retransmission, FEC, low bandwidth, accept remote video,
  adaptive jitter, DSCP (46 audio, 34 video).

## Decisions (`CallCoordinator`, each with a scenario test)

The coordinator keeps what the user wants (`Call.cameraOn`) apart from what the engine negotiated
(`Call.video`), and makes the second follow the first. Its API: `dial(input, account, video)`,
`answer(id, video)`, `setCameraOn(id, on)`, `setVideoEnabled(id, enabled)`, `switchCamera(id)`,
`setDeviceRotation(degrees)`, `statistics(id)`, `quality(id)`.

| Situation | Decision |
|---|---|
| Dial with video | Telecom `CALL_TYPE_VIDEO_CALL`; camera on; speaker unless a headset or Bluetooth is the route |
| Incoming offer with video | Rings as a video call (`Call.videoOffered`, Telecom video); *Answer with video*, *Answer audio only*, *Decline* |
| Answer audio only | Video taken receive-only when offered and *Show video the other side turns on* is on; camera off; the user can turn it on |
| Another device answers (headset, watch, car) | Never with the camera |
| The remote adds video mid-call | Accepted **receive-only** by the engine; if an engine took it with the camera on, the coordinator turns it off (privacy) |
| The user turns the camera on/off | `setVideo(send = on)`; on an audio call, video is added both ways; the foreground service gains or loses the `camera` type |
| Camera changed while ringing or remotely held | Asked once the call is up |
| A video call answered without video | `cameraOn` goes off |
| Video becomes active on the earpiece | The route moves to the speaker, once per stretch of video; a headset, Bluetooth, or a route the user picks after is kept |
| Video ends (both directions off) | The route is left as it is; Telecom told audio; `Call.isVideo` false (proximity lock allowed again) |
| Hold | Video stops with the audio; Telecom keeps its type; resume restores the directions held before (a camera change on hold applies then) |
| Camera permission refused | The call goes on as audio, `Call.cameraBlocked = NoPermission` says why |
| *Turn off video* (`setVideoEnabled(false)`) | Both directions dropped, voice kept; on again: receive-only, camera off |
| Video calls off in settings | Dialed and offered calls are audio |

`Call.isVideo` (video active and neither on hold nor over) is what the screen uses for the video
layout, keeping the screen on and never letting the proximity sensor turn it off.
`CallForeground.Calls(microphone, camera)`: camera while a call's camera is on and it is not held.

## The network

All of it is engine configuration (`EngineConfig.video`, applied in `LinphoneConfigurer`), from
*Settings › Video* (`VideoSettings`, keys `video.*`):

- **Adaptive rate control** (`video.adaptive_rate`, on) with Linphone's `advanced` algorithm; the
  video preset is left `default`, since `custom` would pin the definition.
- **Losses repaired**: retransmission on NACK (`video.retransmission`) and FEC (`video.fec`),
  both offered; RTCP feedback is Linphone's (AVPF is left as the account has it: forcing it would
  make a server without `use_avpf` refuse the call).
- **Adaptive jitter buffers** for audio and video.
- **Bandwidth by network**: `NetworkMonitor` (port in `:calling:domain`, `AndroidNetworkMonitor` in
  `:calling:platform`, the default network's callback) says Wi-Fi, mobile data or none, and
  metered. On Wi-Fi: `video.wifi_quality` (720p) and `video.wifi_bitrate` (1.5 Mbit/s) up, no
  cap down. On mobile data or a metered Wi-Fi: `video.mobile_quality` (VGA) and
  `video.mobile_bitrate` (512 kbit/s) both ways, and Linphone's low-bandwidth mode **for calls
  without video only** (the mode turns video off). Losing the network keeps the last limits.
  `EngineConfigBuilder` rebuilds the configuration when it changes.
- **Priority marking**: DSCP EF (46) for audio, AF41 (34) for video.
- Also `video.enabled`, `video.codecs` (VP8, H.264, H.265), `video.accept_remote`,
  `video.frame_rate` (30 fps).

What the user sees of it:

- A **quality indicator** from `CallQuality.of(statistics)` (or `coordinator.quality(id)`): the
  worst of loss (both streams, both ends: Good < 2 %, Fair < 6 %), the voice's jitter (< 30 ms,
  < 60 ms) and the round trip (< 300 ms, < 600 ms); `null` before anything was measured. Words in
  `:calling:ui` (`CallQuality.label()`, `CameraBlocked.text()`).
- After several seconds of *Poor* in a video call, a banner: "Weak connection: video lowered",
  with *Turn off video* (`setVideoEnabled(false)`) to keep the voice.
- **Frozen video**: when the remote should send but no frame arrived for 3 seconds (received fps
  0 in the statistics), the last frame dims under "Video paused".

## Cameras

- Front by default (found by Linphone's device name, `… Front`; the `StaticImage` stand-in is
  skipped); *Switch camera* picks the device facing the other way and applies it with a `null`
  update, without renegotiating.
- The front preview is mirrored on screen (never in what is sent): the screen's job.
- `Core.deviceRotation` follows the display rotation (`setDeviceRotation`, taken to the nearest
  quarter turn), so the picture sent is upright.
- Camera off is a direction change (`sendrecv`→`recvonly`): nothing is sent, which saves upload,
  and the other side shows its placeholder.
- Linphone never initiates or accepts video by itself (activation policy off both ways): a
  re-INVITE adding video is deferred and accepted with video `recvonly`.

## The platform

- `CAMERA`, `FOREGROUND_SERVICE_CAMERA`, `ACCESS_NETWORK_STATE`, and `android.hardware.camera`
  not required. `CallService` is `phoneCall|microphone|camera`; the camera type is claimed only
  while the camera is on and granted, and dropped first if Android refuses.
- The incoming notification says *Incoming video call* and uses `CallStyle.setIsVideo`; its
  *Answer* answers with the camera.
- Linphone's own consumer rules keep `org.linphone.mediastream.**`, which its JNI calls for video;
  nothing is added to `proguard-rules.pro`.
- The demo engine plays: a call dialed with video is answered with video; `555` turns its camera
  on after a few seconds; `777` is on a weak network (Poor).

## The call screen (to build)

- **Video layout**: the remote picture fills the screen (fit or fill, double tap); the local
  preview is a floating tile that snaps to the nearest corner when dragged, and swaps with the
  remote on tap. Controls fade after a few seconds and come back on a tap.
- **Controls** on a frosted bar (`:core:designsystem` blur): mute, camera on/off, switch camera,
  audio route, more (statistics, keypad, hold), and hang up.
- **Statistics**: a frosted panel, updated every second, with the numbers above and small
  sparklines of bitrate and frame rate for the last minute. On a wide window, a side panel.
- **Responsive**: portrait, landscape (controls on a rail) and wide windows, from the pane context.
- **Picture-in-Picture**: the remote picture in its aspect ratio, with *Mute* and *Hang up*.

Only checkable on a device: Linphone's camera device names, the preview toggle during a call, the
`null` update not sending a re-INVITE, and Telecom's `requestCallType`.

## Asterisk

Video passes through Asterisk when the endpoints allow a common video codec: `allow=...,vp8,h264`
and `max_video_streams=1` on both endpoints. Linphone offers VP8, H.264 and H.265 (hardware
through MediaCodec); the codec order is a setting like the audio one.
