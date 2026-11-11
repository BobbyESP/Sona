# Calling

How a registration and a call work, end to end: the SIP engine, the coordinator that owns calls,
Android's Telecom, the services that keep Sona alive, and how failures reach the user. This is the
part of the app where a mistake means a missed call, so its rules are stricter than anywhere else.

```
            ┌────────────── :calling:domain (plain Kotlin, application scope) ──────────────┐
 screens ──>│ PlaceCall, Answer, Decline, HangUp, Hold, Mute, SendDtmf, SelectAudioRoute    │
 (intents)  │                         │                                                     │
            │                   CallCoordinator (decisions, scenario-tested)                   │
            │                    │    │    │                                                │
            └────────────────────┼────┼────┼──────────────────────────────────────────────┘
                     SipEngine   │    │    │ SystemCalls              CallHistory, Settings
                     (:sip:api)  │    │    │ (:telecom:api)           (ports)
                                 ▼    │    ▼
                  :sip:linphone       │   :telecom:jetpack ──> Core-Telecom CallsManager
                  (Core thread)       │
                                      ▼
                       CallForeground (StateFlow) ──> :calling:platform: KeepAliveService,
                                                      CallService, CallNotifications
```

## The engine contract (`:sip:api`)

Plain Kotlin, no Linphone type, no Android type:

```kotlin
interface SipEngine {
    val state: StateFlow<EngineState>                      // Stopped, Starting, Running, Failed(reason)
    val capabilities: Set<Capability>
    val registrations: StateFlow<Map<AccountId, RegistrationState>>
    val calls: StateFlow<List<EngineCall>>                 // every call the engine knows, incoming included
    val trace: Flow<SipTraceEvent>                         // only while tracing is on

    suspend fun start(config: EngineConfig)
    suspend fun stop()
    suspend fun apply(config: EngineConfig)                // codecs, network, encryption, logging
    suspend fun setAccounts(accounts: List<AccountSpec>)   // the whole set the engine must have
    suspend fun refreshRegistrations()                     // after a network change
    suspend fun dial(account: AccountId, to: SipAddress, options: DialOptions): DialResult
    fun controls(call: CallId): CallControls?              // answer, decline, hangUp, hold, mute, dtmf…
}
```

- **The engine is told the whole state, not edited step by step.** `setAccounts` receives every
  account that should exist, with its credentials, and the engine adds, updates and removes until
  it matches. Accounts live in Room, passwords in the vault; Linphone keeps nothing the app relies
  on. Two sources of truth that must agree (Room and `linphonerc`) is the bug Docucraft's
  navigation doc warns about, in another place.
- **`EngineCall` is what the engine knows**: SIP state, remote address, media (codec, encryption,
  ZRTP SAS), stats, the SIP status of its end. The *user's* view of a call, with Telecom's state and
  the contact merged in, is `Call`, built by the coordinator.
- **Capabilities are declared**, so features can hide what an engine lacks (see
  [architecture.md](architecture.md#capabilities)).

## Linphone (`:sip:linphone`)

`org.linphone:linphone-sdk-android` 5.5.39 from `https://download.linphone.org/maven_repository`,
with the repository filtered to the `org.linphone` groups. Sona uses the `org.linphone.no-video`
variant: Sona makes audio calls only, and the variant is the same API without the video codecs and
camera code, so the APK is smaller. Should video ever be wanted, it is the `org.linphone` artifact,
one line in this module.

- **One thread owns the Core.** Linphone's `Core` is not thread safe. The module starts a
  `HandlerThread` ("sona-core"), creates the Core on it, iterates it every 20 ms while it runs, and
  exposes that thread as a coroutine dispatcher. Every public function switches to it, every
  listener runs on it, and what leaves is an immutable domain value in a `StateFlow`. Nothing in the
  app ever touches the Core from the main thread.
- **Listeners are translated at the edge.** `CoreListenerStub` callbacks become `EngineCall` and
  `RegistrationState` updates in `LinphoneMapper.kt`, the only file that knows both vocabularies.
  SIP reasons and status codes are kept as numbers and phrases (`SipStatus(486, "Busy Here")`): the
  domain decides what they mean.
- **The SIP trace comes from the logs.** Belle-SIP logs every message it sends and receives;
  `LinphoneTrace` parses those log lines into `SipTraceEvent`s (direction, method or status,
  Call-ID, CSeq, raw text) while tracing is on, for the diagnostics screen's ladder.
- **Audio routing is not Linphone's job.** See [Audio](#audio).
- **Auth infos are rebuilt whole.** On every change of the accounts, the Core's auth infos are
  cleared and added again. Linphone keeps its own copy of each one, so removing ours by object can
  miss it, and a replaced account would quietly register with its old password (found on a device
  in phase 1, and now a test).
- **Its own `503 io error` is not the server's.** When Linphone cannot send at all it reports a
  local 503; the mapper reads the reason (`IOError`) and says the server was not reached.
- **Verbose logs in debug builds.** `EngineConfig.verboseLogs` mirrors Linphone's log, every SIP
  message included, to logcat under `SonaSip`.
- **Config is pushed, never read.** `EngineConfig` (codec order and enablement, Opus bitrate, echo
  cancellation, adaptive rate, jitter buffer, RTP port range, STUN/ICE/TURN, keep-alive, DSCP,
  media encryption, TLS verification, user agent, log level) is applied in `LinphoneConfigurer`.
  An option that needs re-registering triggers it; one that only reaches new calls is marked so in
  its setting.

## The coordinator (`:calling:domain`)

`CallCoordinator` is a Koin `single` started by `App` in the application scope. It is the only thing
that changes a call, and the only thing screens talk to about calls.

- **It merges two state machines.** The engine knows SIP; Telecom knows what the system, the
  Bluetooth headset and the watch think. A call is active for the user only when both agree.
- **Everything goes through one lock, in order.** An engine update, the user's answer and a
  headset's hang-up never interleave. The pure parts (addresses, why a call ended, which call the
  screen shows) are functions with table tests; the choreography between engine and Telecom is
  covered by scenario tests against `FakeSipEngine` and `FakeSystemCalls`, on the JVM.
- **An ended call stays until the coordinator lets it go.** The engine keeps it as `Ended` until
  `forget`; the coordinator shows it for three seconds (the call screen says how it ended), then
  forgets it. A call that fails before it is first seen (busy, no such extension) is still shown
  with its reason: found by a test, it was being dropped unexplained.
- **It publishes `calls: StateFlow<List<Call>>`** and `CallForeground` flow
  (see `CallCoordinator.kt` for its cases), which `:calling:platform` turns into
  services. The domain never starts a service itself.

```kotlin
data class Call(
    val id: CallId,
    val account: AccountId,
    val direction: Direction,
    val remote: Party,                  // address, display name, matched contact
    val state: CallState,               // Dialing, RingingRemote, Incoming, Connecting, Active,
                                        // OnHold, HeldByRemote, Ending, Ended(EndReason)
    val muted: Boolean,
    val audioRoute: AudioRoute,
    val availableRoutes: List<AudioRoute>,
    val media: MediaInfo?,              // codec, sample rate, encryption, SAS
    val quality: CallQuality?,          // Good / Fair / Poor, with jitter, loss, RTT
    val connectedAt: Instant?,
)
```

### An outgoing call

1. The user taps Call. `PlaceCall(input, account?)` resolves the address (below), picks the account
   (the one chosen, the default, or asks), and checks, in order: microphone permission, account
   registered, network. Each failure is a `DialOutcome` the dialer explains; nothing is dialed.
2. The engine dials. Telecom is told an outgoing call exists (`addCall`, direction outgoing), and
   `CallService` goes foreground with the call's notification within the platform's 5 seconds.
3. `180 Ringing` or early media: `RingingRemote`. `200 OK`: Telecom `setActive()`, then `Active`.
4. Either side hangs up: Telecom `disconnect(cause)`, the record is written, the service stops when
   no call is left.

### An incoming call

1. The engine reports an `INVITE`. The coordinator applies policy first, in order: *Do not disturb*
   (decline with `486` or `603`, per setting), busy with call waiting off (`486`), auto-answer (the
   setting, or Asterisk's `Call-Info: answer-after` / `Alert-Info` header for paging, if allowed).
2. Otherwise Telecom is told (`addCall`, direction incoming), `CallService` goes foreground, and the
   incoming-call notification is posted: `CallStyle.forIncomingCall`, full-screen intent to
   `CallActivity`, *Answer* and *Decline* actions.
3. Answer from the notification, the call screen, a headset or a watch: Telecom `answer()`, then the
   engine answers, then `Active`. The order matters: Telecom first, so the system has moved audio
   to the call before media starts.

### Calls from other devices

Telecom calls `onAnswerCall`, `onSetCallActive`, `onSetCallInactive` and `onSetCallDisconnected`
when a headset, a watch or a car acts on the call, and **gives the app 5 seconds** to carry it out,
or it may tear the call down. The coordinator answers them through the same decision path, with the
engine call bounded to 4 seconds. A call that cannot be held (the remote side refused the
re-INVITE) throws back to Telecom, which is what Telecom expects.

### More than one call

The MVP supports one active call and one waiting: answering a second call holds the first (or ends
it, if the user chooses *End and answer*), and the screen swaps between them. The engine and the
model already allow any number; conferences and attended transfer only add commands.

## Addresses

`AddressResolver` (pure, tested) turns what the user typed or tapped into a `SipAddress`:

| Input | Becomes | Note |
|---|---|---|
| `sip:alice@lab.local`, `sips:…` | itself | |
| `alice@lab.local` | `sip:alice@lab.local` | |
| `102`, `*97`, `600#` | `sip:102@<account domain>` | the Asterisk extension case, the common one |
| `+34 600 11 22 33`, `(600) 112-233` | `sip:+34600112233@<domain>` | spaces and punctuation removed; per-account prefix and strip rules applied |
| `tel:` intents | as a number | |
| anything else | `Invalid(reason)` | shown under the field, not as an error after dialing |

## Why a call ended

`EndReason` is decided in the domain from the SIP status, and has two texts (see
[architecture.md](architecture.md#results-not-exceptions)):

| SIP | `EndReason` | What the user reads |
|---|---|---|
| `BYE` / `CANCEL` | `HungUp(byMe)` | Call ended · 3:42 |
| `486`, `600` | `Busy` | 102 is busy |
| `603` | `Declined` | 102 declined the call |
| `480` | `Unavailable` | 102 is not online right now |
| `404`, `484` | `NotFound` | There is no extension 205 on *Lab Asterisk* |
| `408`, no answer | `NoAnswer` | No answer |
| `401`, `407`, `403` | `Unauthorized` | The server refused the call. Check this account's username and password |
| `488`, `415` | `NoCommonMedia` | No audio format both sides support. Enable PCMU or PCMA, or Opus on the server |
| `503`, transport error | `ServerUnavailable` | *Lab Asterisk* (192.168.1.10:5060, UDP) did not answer |
| anything else | `Failed(status)` | Call failed (`500 Server Internal Error`) |

Registration failures use the same mapping (`RegistrationState.Failed`), shown on the account's
status chip and in its diagnostics.

## Audio

**Telecom owns the route.** (Built in phase 2: the call screen offers Telecom's endpoints and
requests changes; Linphone's voice streams follow the device Telecom picks. On the emulator Telecom
offers only the speaker; earpiece and Bluetooth are still to be checked on a phone.)

 With Core-Telecom, the app must not call
`AudioManager.setCommunicationDevice` or `startBluetoothSco`. The call screen offers
`availableEndpoints` (earpiece, speaker, wired headset, each Bluetooth device by name), and choosing
one calls `requestEndpointChange`. When `currentCallEndpoint` changes, the coordinator tells the
engine, which selects the Linphone `AudioDevice` of the same type, so capture and playback follow
the system's route. Media plays on `STREAM_VOICE_CALL`.

This is the riskiest seam in the app (two libraries that both believe they route audio), so it is
the first spike of the calling phase: speaker, earpiece, wired, Bluetooth classic and LE audio,
switched during a call, on Android 10, 13, 14 and 16. See [roadmap.md](roadmap.md).

Mute goes through Telecom too (`isMuted` is a flow there), so the headset's mute button and the
screen's agree.

## Staying reachable

Asterisk does not push. To ring while the app is closed, Sona must stay registered, which on
Android means a foreground service. There are two services, for two needs:

| Service | Type | Runs while | Notification |
|---|---|---|---|
| `KeepAliveService` | `specialUse` ("keeps SIP accounts registered to receive calls from servers without push") | *Always reachable* is on and an account is enabled | Silent, low importance: "Reachable on Lab Asterisk · 102" |
| `CallService` | `phoneCall`, plus `microphone` once the user answered or dialed | a call exists | `CallStyle`: incoming, then ongoing |

- **Telecom's calls live in `CallService`**, as Core-Telecom requires on Android 14 and later.
  It is started when a call is added, with the call's notification, within 5 seconds.
- **`microphone` is added when the user answers or dials.** Android only lets a service use the
  microphone when it starts from a user action or a visible activity, and answering or dialing is
  both. Ringing needs no microphone, so the incoming call itself never depends on that exemption.
  How each Android version (10 to 16) treats this is checked in the calling phase's first week.
- **On boot, only `KeepAliveService` may start.** Android 15 forbids starting a `phoneCall` service
  from `BOOT_COMPLETED`; `specialUse` is allowed. The boot receiver starts the keep-alive service
  and nothing else.
- **Keeping the registration up** is the engine's work: the registration is refreshed before it
  expires, NAT keep-alives (UDP) or TCP/TLS connection keep-alives are sent on the interval set,
  and a network change should refresh registrations immediately (planned: `NetworkMonitor`; first
  see whether Linphone already does it, docs/todo.md).
- **The user decides.** *Always reachable* is on by default with an enabled account, explained in
  onboarding; off, Sona registers while open and unregisters a minute after leaving.

### Call readiness

Several system settings silently break incoming calls. Each is a `ReadinessCheck` shown in
onboarding and in *Settings › Call readiness*, with a button that fixes it:

| Check | Why | Fix |
|---|---|---|
| Local network access (Android 17+) | a server on the local network (the lab's Asterisk) cannot be reached: Linphone fails to send with *Operation not permitted* | `ACCESS_LOCAL_NETWORK`, asked when an account is checked (built in phase 1) |
| Microphone permission | no audio | runtime request |
| Notifications (Android 13+) | no incoming call notification | runtime request |
| Full-screen notifications (Android 14+) | calls ring as a banner over the lock screen instead of a screen | `NotificationManager.canUseFullScreenIntent()`, opens `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` |
| Battery optimization | the system may stop the keep-alive service | requests the exemption; check Play policy before publishing there |
| Incoming-call channel muted | silent calls | opens the channel's system settings |
| At least one account registered | nothing can ring | opens the account |

## Notifications

- **One channel for incoming calls** (per account later), high importance, with the ringtone as
  the channel's sound and the insistent flag. The system then applies Do not
  disturb, the ringer volume and vibration on its own, and the user can give each account its own
  ringtone in the system's settings.
- Channels for ongoing calls (silent), missed calls (with *Call back*), and the keep-alive service.
- Notification content follows *Hide caller on the lock screen* (`VISIBILITY_PRIVATE` with a public
  version that only says "Incoming call").

## The call activity

`CallActivity` (`:app`) hosts the call screens. It is separate from `MainActivity` because a call
has needs no other screen has:

- **It shows over the lock screen** (`setShowWhenLocked`, `setTurnScreenOn`) and is the full-screen
  intent of the incoming call notification. Unlocking is asked for only when the user leaves the
  call for the app.
- **It runs in its own task** (`taskAffinity`, `singleInstance`-like launch), so finishing a call
  leaves the user where they were, and recents show the call, not a copy of the app.
- **Its back stack is its own**, rooted at `IncomingCall` or `OngoingCall`, with the keypad, audio
  route sheet, call details and (later) transfer as destinations. When the call ends, the screen
  shows how it ended for a moment ("Call ended · 3:42", or why it failed) and finishes.
- **The proximity sensor turns the screen off** while the earpiece is the route
  (`PROXIMITY_SCREEN_OFF_WAKE_LOCK`), and never on speaker or Bluetooth.

The main app shows an ongoing-call banner (`:calling:ui`) above every screen while a call exists;
tapping it brings `CallActivity` back.

## History

The coordinator writes a `CallRecord` when a call ends (account, remote, direction, start, connect,
duration, `EndReason`, codec, quality summary), through the `CallHistory` port of
`:directory:domain`. Missed calls also post a notification.

With `ACTION_CALL_BACK` registered, Telecom also logs Sona's calls in the system's call history
(Core-Telecom 1.1); *Show Sona calls in the phone app* sets `isLogExcluded` for users who do not
want that. The system's *Call back* comes back as a standing request.

## What is not survived

The Core lives in the app's process. If the process dies during a call, the call ends: Telecom
disconnects it, and the next start registers again. A call is not restored after process death;
pretending otherwise would show a call that has no media.
