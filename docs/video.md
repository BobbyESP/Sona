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

## The call screen

In `:feature:call`, from `CallViewModel`'s state; the screen never owns a call, it sends intents
and the coordinator decides. Its logic is in pure functions, tested on the JVM
(`video/Watches.kt`, `video/Geometry.kt`, `video/Statistics.kt`, `CallViewModelTest`).

```
┌──────────────────────────────┐
│ ( Alice  03:42 🔒 Lab   ▂▄▆ )│  frosted top line; the bars open the statistics
│ ( Weak connection … [Turn off video] ✕ )   one banner at a time
│                       ┌────┐ │
│                       │you │ │  the preview tile: dragged, snaps to a corner, tap swaps
│     the other side    └────┘ │
│     (fill or fit: double tap)│
│ ( 🎤  📷  ⟲  🔊  ⋮ )          │  frosted bar: mute, camera, switch, route, More
│        ( ☎ hang up )         │
└──────────────────────────────┘
```

- **Layout**: `call.isVideo` takes `VideoCallScreen`; everything else stays the audio screen.
  The other side's picture fills the stage (`VideoStage`), laid out at the aspect of
  `receivedSize` (`videoFrame`), cropped (fill) or whole (fit), toggled by a double tap. This
  side's camera is a floating tile (`MaterialTheme.shapes.large`) that follows the finger and,
  let go, snaps to the corner its centre is nearest, a fling carrying it further
  (`nearestCorner`), on the motion scheme's spatial spring; a tap swaps the two. Both views keep
  their place in the composition when they swap (only their modifiers change), so a swap never
  builds a view again. The front camera is mirrored on screen (`TextureView.scaleX = -1`), never
  in what is sent. While the other side sends nothing, or this side's camera is off, a
  placeholder takes the picture's place: their mark on its Material shape, as on the audio
  screen, with what is happening ("Calling…", "Alice is not sending video", "Your camera is off").
- **Surfaces**: two `TextureView`s in `AndroidView`s, handed to `VideoSurfaces.attach` in a
  `DisposableEffect` keyed by the call and the views, and detached (`null`, `null`) when it goes.
  The preview view exists only while the camera is sent.
- **Rotation**: a `DisplayManager.DisplayListener` reports the display's rotation (a turn from 90
  to 270 included, which changes no configuration) to `coordinator.setDeviceRotation` through the
  ViewModel, once per change. `CallActivity` handles its own configuration changes (orientation,
  size), so turning the phone or entering Picture-in-Picture keeps the views.
- **Controls** fade 4 seconds after the last touch in an answered video call
  (`CallUiState.controlsAutoHide`: not while *More*, the keypad or the statistics are open); a tap
  on the picture brings them back, or hides them. On a frosted bar (`frosted`, over the stage's
  `blurSource`): mute, camera on/off, switch camera (while the camera is sent), the audio route
  (the same control as the audio screen's, drawn as an icon), *More* (statistics, keypad, hold or
  resume) and hang up, which stays in reach when the rest has to scroll. Upright, the bar is at the
  bottom; on a screen wider than tall it is a rail along the end edge. Every control is an icon
  with its words for TalkBack, so nothing is cut at a large font.
- **Audio calls** get a *Video* control (when `Capability.VideoCalls` and *Video calls* are on):
  it turns the camera on (`setCameraOn(id, true)`), which adds video both ways.
- **The camera permission** is asked at the moment it is needed (turning the camera on, *Answer
  with video*, *Video call*); a refusal still sends the intent, the coordinator keeps the camera
  off and says why (`cameraBlocked`), and the screen shows it as a banner with its technical line
  and *Settings* (the app's page in Android's settings).
- **Incoming video call**: "Incoming video call", and *Decline*, *Answer audio only* and *Answer
  with video*; with the camera already allowed, this side's camera is behind it, under a veil
  (`PreviewBackdrop`). The notification's *Answer* keeps answering with the camera through
  `CallActivity`.
- **Banners**, one at a time, the most pressing first: the camera blocked; "Weak connection: video
  lowered" with *Turn off video* (`setVideoEnabled(false)`), after five *Poor* seconds in a row in
  a video call (`WeakConnectionWatch`; dismissed, it stays away until the connection recovers and
  weakens again); "Alice turned on video" with *Turn on camera*, when the other side's picture
  starts arriving in an answered call with this side's camera off (`remoteTurnedOnVideo`), for
  ten seconds.
- **Frozen video**: when a picture is expected (`showsRemoteVideo`) and three samples in a row
  received no frame (`FrozenVideoWatch`), the last frame dims under "Video paused".
- **The quality indicator** (`QualityIndicator`): three bars and the grade's words for TalkBack,
  in the video screen's top line and the audio screen's header; a tap opens the statistics.
- **Statistics** (`StatisticsPanel`), frosted: video codec, size and frame rate sent and received,
  kbit/s up and down, loss here and there, jitter, round trip, jitter buffer, FEC repaired,
  estimated bandwidth; audio codec and clock rate, kbit/s, loss, jitter, round trip, jitter buffer;
  SIP transport, ICE, IP family, encryption and SRTP suite. Numbers in
  `SonaTheme.typography.monospace`, each line wrapping rather than cutting at a large font.
  Sparklines (`Canvas`, `primary` and `tertiary`) of the bitrate up and down and the frame rate
  sent and received, over the last minute, from a fixed 60-sample ring buffer (`StatsHistory`). On
  a window 600 dp wide or more the panel is beside the picture; on a phone it rises from the
  bottom.
- **What is measured, and when**: the ViewModel collects `coordinator.statistics(id)` once a
  second only while the screen is started (Picture-in-Picture is; the home screen is not), and
  derives from it the quality, both watches and the history. The numbers and the history reach
  the state only while the panel is open, so a closed panel recomposes nothing.
- **Proximity and the screen**: the proximity lock is never taken during video; the screen stays
  on for the whole call.
- **Picture-in-Picture** (`CallActivity`, `supportsPictureInPicture`, `resizeableActivity`): an
  answered video call goes there when the user leaves (`onUserLeaveHint` below Android 12,
  `setAutoEnterEnabled` from Android 12, both only while there is one), in the shape of the
  picture received (`pipAspect`, kept within Android's 1:2.39 to 2.39:1, 9:16 until known), showing
  the other side alone. Its actions, *Mute* (or *Unmute*) and *Hang up*, are broadcasts to
  `CallActionReceiver`, which hands them to the coordinator.
- **Video call** wherever a party is shown: `VideoCallAction`, a `PartyActionProvider` (order 10)
  that asks for the microphone and the camera, dials with video and opens the call screen
  (`LocalCallScreen`); not offered without the engine's capability or with video calls off.

Only checkable on a device: Linphone's camera device names, the preview toggle during a call, the
`null` update not sending a re-INVITE, Telecom's `requestCallType`, the angle Linphone expects for
the display's rotation, the frost over a `TextureView`, Picture-in-Picture's entry and its
actions, and the incoming call's camera behind it (the Linphone engine runs the preview only while
the camera is sent, so it may stay dark until the call is answered with video).

## Asterisk

Video passes through Asterisk when the endpoints allow a common video codec: `allow=...,vp8,h264`
and `max_video_streams=1` on both endpoints. Linphone offers VP8, H.264 and H.265 (hardware
through MediaCodec); the codec order is a setting like the audio one.
