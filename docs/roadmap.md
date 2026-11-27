# Roadmap

Sona ships a calling core first and grows into the rest. Every phase ends with something that
works end to end against a real Asterisk, and every later feature already has its place: a module
name, a contribution point, a capability, a setting declaration. Sizes are in weeks of one person's
work and are estimates.

## Phases

### Phase 0 · Foundations (1 week)

**Done (2026-11-09).** Built: `build-logic` with five convention plugins (Room's comes with
`:core:database`), the version catalog, Spotless, CI, `:core:{model,common,data,designsystem,ui,
navigation,testing}`, `:sip:api`, `:sip:fake`, the Recents, Contacts and Keypad tabs (the keypad
types; calling is off until phase 2), the `demo` flavor and `lab/`. 47 JVM tests. Checked on a
Pixel 10 Pro XL emulator: tabs, rotation, process death, landscape layout. Not yet checked: CI on
GitHub (no repository yet) and the `lab/` files against a live Asterisk.

- Repository, `build-logic` with the six convention plugins and the feature allow list, version
  catalog ([`gradle/libs.versions.toml`](../gradle/libs.versions.toml)), ktfmt/Spotless, CI
  (build, JVM tests, lint) as in Docucraft's `.github`.
- `:core:*` modules: `BaseViewModel`, `StringProvider`, `SettingsStore` with typed `Setting`s,
  `SonaTheme` and the design system copied from Docucraft/Metadator, `SonaNavDisplay`, `Navigator`
  with top-level switching, the contribution point contracts.
- `:sip:api` with `:sip:fake`, and the `demo` flavor running on it: the shell, an empty Recents and
  a fake account whose chip says *Registered*.
- `lab/`: the Asterisk configuration below, running.

**Done when** the demo flavor starts, navigates between the three tabs, survives rotation and
process death, and a feature that depends on `:sip:linphone` fails the build.

### Phase 1 · Accounts and registration (1–2 weeks)

**Done (2026-11-09).** Built: `:core:database` (Room, schema 1 exported), `:credentials:*`
(AES-GCM under an Android Keystore key), `:accounts:{domain,data,ui}`, `:sip:linphone`
(registration), `:feature:accounts` (switcher sheet, editor with *Check and save*, delete),
`:feature:onboarding`, the `full` flavor. No Linphone cloud: accounts are the user's own
extensions, on the phone only. Checked on the emulator against the Docker lab (`lab/compose.yaml`,
Asterisk 20.6): register 102, a wrong password explained as `401`, back to registered. 80 JVM
tests, 3 device tests. Found on the way: Android 17 blocks local-network traffic without
`ACCESS_LOCAL_NETWORK` (now asked in context), and Linphone kept a replaced account's old password
(fixed, tested). Not yet checked: TCP and TLS, two accounts at once, network changes, a real phone.

- `:core:database` (accounts, overrides), `:credentials:keystore`, `:feature:accounts`,
  `:feature:onboarding` (welcome, add account, check and save).
- `:sip:linphone`: Core on its own thread, `setAccounts`, registration states, `EngineConfig` for
  transport and expiry, `LinphoneMapper`, the trace from logs.
- `RegistrationChip` and the account switcher; the failure mapping for registrations.
- `NetworkMonitor` and refreshing registrations on network changes.

**Done when** an extension on the lab's Asterisk registers over UDP, TCP and TLS, a wrong password
says so in words, turning Wi-Fi off and on re-registers within seconds, and two accounts on two
servers stay registered together.

### Phase 2 · Calls (2–3 weeks). The heart of the MVP

**Done (2026-11-09).** Built: calls in `:sip:api`, `:sip:linphone` and `:sip:fake` (scriptable, and
self-playing in the demo flavor), `:calling:domain` (`CallCoordinator`, `AddressResolver`,
`EndReason`), `:telecom:api` and `:telecom:jetpack` (Core-Telecom), `:calling:platform` (call and
keep-alive services, CallStyle notifications, boot receiver), `:feature:call` and `CallActivity`
over the lock screen, the keypad's call button. Checked on the emulator against the Docker lab:
600 (echo, audio both ways, about 600 packets each way, no loss), 486 ("486 is busy"), 199
("There is no extension 199"), hold and resume (sendonly / sendrecv), mute, DTMF, hang-up, an
incoming call answered from the screen, an incoming call declined (603) with the phone asleep and
the app in the background, the keep-alive service. 109 JVM tests.

Found on the way: Telecom refuses disconnect causes other than local, remote, missed and rejected
(it crashed on a busy call; mapped and guarded); the Docker lab advertised its container address,
so audio went nowhere (now `EXTERNAL_ADDRESS`); a call that failed at once was dropped unexplained
(fixed, tested). Not yet checked: a real phone, earpiece and Bluetooth routes, two phones calling
each other, call waiting with two real calls, Android 10 to 13, a call screen readiness checklist
(the permissions are asked in context instead).

- **First, a three-day spike** on the two risky seams, on Android 10, 13, 14 and 16 (emulators and
  at least one real phone with Bluetooth): audio routes owned by Telecom and followed by Linphone;
  starting `CallService` with `phoneCall` and adding `microphone` on answer, from the background and
  over the lock screen. The spike's findings update [calling.md](calling.md) before the rest starts.
- `:calling:domain`: `CallMachine` with its table tests, `CallCoordinator`, use cases,
  `AddressResolver`, `EndReason`.
- `:telecom:jetpack`, `:calling:platform` (services, notifications, channels, boot receiver),
  `CallActivity` with `IncomingCall` and `OngoingCall`, `:feature:call`.
- Mute, hold, speaker and Bluetooth routes, DTMF keypad, call waiting (hold and answer, end and
  answer), proximity sensor, ended summary.
- *Always reachable* and `KeepAliveService`; Call readiness with its checks.

**Done when** the lab's call matrix passes (below), an incoming call rings with the screen off and
locked after the app has been in the background for an hour, and a Bluetooth headset answers, mutes
and hangs up.

### Phase 3 · Directory: recents, contacts, keypad (1–2 weeks). MVP complete

**Built (2026-11-09), with parts left for the next round.** Built: `:directory:{domain,data,ui}`
(Room schema 2 with an automatic migration; the phone's contacts through `ContactsContract`, read
only and asked for in context), `CallRecorder` (writes the history from the calls the coordinator
publishes, so no screen can forget one), `:calling:ui`, Recents (All and Missed, grouped by day and
by consecutive calls, details sheet with the SIP status, delete, call back, add to contacts),
Contacts (Sona's own and the phone's, favorites, search, editor), T9 suggestions on the keypad,
contact names on the call screen and in Telecom (`CallerNames`), the missed-call notification with
*Call back*, do not disturb (refuse as `486` or `603`), `:feature:settings` (contributions, search,
Calls, Contacts and history, Appearance) and `:feature:diagnostics` (overview). Checked on the
emulator with the `demo` flavor: a call recorded as *Busy*, a contact created and found from the
keypad by its letters, settings and its search, diagnostics. 134 JVM tests.

Not done yet: the split call button; Sona's calls in the phone app's history and `ACTION_CALL_BACK`;
a badge on the Recents tab; diagnostics' SIP trace, logs and sharing (they need an engine
capability for the trace); the MVP options of [ux.md](ux.md#the-options-catalogue) that are still
missing (auto-answer, answer by swipe, vibrate, proximity sensor, DTMF method, codec order, echo
cancellation, speaker by default, dialpad tones, mobile data, start on boot, hide caller on the lock
screen, name and sort order, *Show SIP details*, trace capture, log level, language, reset
everything). Not checked: the Linphone flavor against the lab (Docker was not running), the
missed-call notification on a device, a real phone. A call still in progress when the process dies
is not recorded. The full list, with what earlier phases left, is in
[What is left](#what-is-left).

- `:directory:*`: history written by the coordinator, local contacts, device contacts, favorites,
  the T9 matcher.
- `:feature:history`, `:feature:contacts`, `:feature:dialer` with suggestions and the split call
  button; missed call notifications with *Call back*; system call history and `ACTION_CALL_BACK`.
- `:feature:settings` with every **MVP** option of [ux.md](ux.md#the-options-catalogue) and
  settings search; `:feature:diagnostics` with overview, SIP trace list and logs, and sharing.

**Done when** someone who has never seen Sona adds an extension, calls another, finds the call in
Recents, calls back from a contact, and changes the codec order, without help. **This is the
release for the course.**

### Phase 4 · Depth (2 weeks)

- Every non-MVP option in the catalogue: dial plans, STUN/ICE/TURN, port ranges, QoS, gains, jitter
  buffer, Opus tuning.
- Media encryption (SRTP, ZRTP with its SAS on the call screen, DTLS-SRTP), TLS certificate
  verification with an imported CA.
- Diagnostics: the SIP ladder, network test, call statistics on the call screen.
- Export and import; QR provisioning (`:provisioning:api`, `:provisioning:qr` on the Google code
  scanner, which needs no camera permission).

### Phase 5 · Growth

Each item is a new module plus contributions, without editing the features above:

| Feature | Module(s) | Plugs in through |
|---|---|---|
| Blind and attended transfer, add call, conference | `:feature:transfer` | `CallActionProvider`, new `CallMachine` commands |
| Voicemail and MWI | `:feature:voicemail` | `TopLevelDestination` (badge), `Mwi` capability, account setting |
| Presence and BLF (Asterisk hints) | `:presence:*`, `:feature:presence` | `PartyActionProvider`, a state on `Avatar` |
| Messaging (SIP `MESSAGE`) | `:messaging:*`, `:feature:messages` | `TopLevelDestination`, `PartyActionProvider` (**built 2026-11-27**) |
| Call recording | `:feature:recording` | `CallActionProvider` |
| Widgets and Quick Settings tiles (favorites, *Always reachable*, DND) | `:feature:widgets` | standing requests |
| AppFunctions ("call extension 102") | `:feature:appfunctions` | the same use cases |
| Real-time text | engine capability | `CallActionProvider` |

Then, once Sona is stable: the design system shared by Docucraft, Metadator and Sona as one
library.

**Built ahead of this phase (2026-11-27): video calls and messaging.** Video
([video.md](video.md)): the engine contract, Linphone video (VP8, H.264, H.265), the coordinator's
camera decisions with their scenarios, network-aware bandwidth, the video call screen, statistics
and the quality indicator, Picture-in-Picture, *Settings › Video*, *Video call* as a party action.
Messaging ([messaging.md](messaging.md)): SIP `MESSAGE` in both engines, Sona's envelopes
(receipts, typing, long texts, files in chunks that survive a restart), Room schema 4, the
Messages tab, notifications with *Reply*, *Message* as a party action; `PartyActionProvider` is
built, hosted by Contacts and Recents. The lab relays both. Checked: the JVM tests (382 runs),
both debug flavors, lint on `full`, a final review. **Nothing of it has been seen on a device**;
what to check is in [todo.md](todo.md#video-calls).

## What is left

**The short list of what remains and what comes next is [todo.md](todo.md).** This section is
the record behind it: what was found, when, and where each item was checked.

From a review of the code on 2026-11-10, against this roadmap, [ux.md](ux.md) and
[calling.md](calling.md). That day: 134 JVM test runs passed, both debug flavors and the `full`
release (through R8, unsigned) assembled, Spotless was clean, and lint failed with the two errors
listed under *Release*. The same day the `full` debug build was run on a Pixel 9 Pro XL (Android
17) against the Docker lab over Wi-Fi: what that showed is under
[Found on a real phone](#found-on-a-real-phone), and the items it checked are ticked. Everything
else is read from the code and still has to be seen happening.

Tick an item when it is built **and** checked; say where it was checked in the phase's paragraph
above.

### 1 · The MVP (phase 3's "done when")

**Call readiness**

- [ ] `ReadinessCheck` in `:core:ui`, its checks (microphone, notifications, full-screen
      notifications, battery optimization, the incoming channel muted, an account registered), the
      *Call readiness* screen in settings and the step in onboarding. **Built 2026-11-11**: the contract and
      `ReadinessMonitor` (tested) in `:core:ui`, the six checks in `:calling:platform`, the screen
      in `:feature:onboarding`, first in *Settings › Calls* and after the first account added from
      the welcome. The account check asks for an account that is on, not a registered one: a
      failed registration is already what the chip says. The battery check only applies while
      *Always reachable* is on, and asks for the exemption directly
      (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: to weigh against Play's policy before publishing
      there). None of the checks or fixes was seen on a phone.
- [ ] Ask for notifications before the first call can arrive, not at the first call made. Today
      they are asked for only when dialing or answering on screen (`CallPermissions.kt`,
      `CallScreen.kt`). Seen on the phone: without the permission the incoming call's notification
      is still posted (Android exempts a call's), but the missed-call notification and the
      keep-alive one are not, so a call missed on a fresh install leaves nothing behind.
      **Built 2026-11-11** as the readiness step after the first account; someone who adds the account another
      way is told by the chip instead.
- [ ] The account chip turns to a warning on a readiness problem, and the switcher starts with it.
      **Built 2026-11-11**.

**Settings that reach the engine**

- [ ] `EngineConfig` carries more than the user agent and the log switch; `EngineConfigBuilder` in
      `:calling:domain` builds it from the settings, `LinphoneConfigurer` applies it, and `apply()`
      does more than set the user agent. **Built 2026-11-11** (the builder, the codec plan and the
      ranked setting are tested on the JVM, both flavors assemble); not yet seen on a phone.
- [ ] Per-account overrides: `Override<T>`, `EffectiveSettings`, their storage with the account,
      and *Uses app default* / *Override* in the account editor. **Built 2026-11-11** for codecs and the DTMF
      method: Room schema 3 (`account_overrides`, an automatic migration from 2), carried to the
      engine in `AccountSpec`. Linphone has one codec list and one DTMF way for the whole Core, so
      they are set for the call's account before each offer, answer and digit. To check on a
      phone: the migration on an installed version 2, and that an answer (not only an offer)
      follows the account's codecs.
- [ ] Codec order and enablement (named by the "done when"), DTMF method, echo cancellation, mobile
      data or Wi-Fi only, log level. **Built 2026-11-11**, each with its item and keywords, in the
      new *Audio* and *Network* areas, *Calls* and *Diagnostics*. To check against the lab: that
      the SDP offer follows the order (it should also settle "every call negotiated PCMU"), each
      DTMF method against `*97`, registration dropping on mobile data with Wi-Fi only.

**The MVP options still missing** (14 exist: call waiting, always reachable, do not disturb and how
to decline, grouping, tap action, confirm before calling back, retention, clear history, device
contacts, theme, dynamic color, pure black, palette style)

- [ ] Calls: account for calls (default or ask every time), auto-answer with its delay and speaker,
      full screen or banner when unlocked, answer by swipe, vibrate when ringing, the proximity
      sensor's switch.
- [ ] Audio: start calls on speaker, dialpad tones, the dialpad haptics' switch (they are always on).
- [ ] Network: start on boot (it always does).
- [ ] Privacy: hide caller on the lock screen (the incoming channel is `VISIBILITY_PUBLIC`, and
      there is no public version of the notification).
- [ ] Contacts and history: only contacts with a SIP address or number, name order and sort order,
      Sona's calls in the phone app's history.
- [ ] Appearance: high contrast, the seed color's picker, dialpad letters, addresses under names,
      app language (with `localeConfig`).
- [ ] Diagnostics: *Show SIP details* (the call screen always shows the status line), reset
      everything. SIP trace capture and log level: **Built 2026-11-11**.
- [ ] Accounts: choosing the color (it is assigned), the ringtone through a channel per account.
- [ ] Each of them in the catalogue's items with keywords, as AGENTS.md asks.

**Diagnostics**

- [ ] The trace capability: `SipEngine.trace`, `LinphoneTrace` parsing Belle-SIP's log lines
      (tested), the fake engine's scripted trace, `Authorization` headers redacted. **Built 2026-11-11**. The two
      log formats were read from liblinphone 5.5.39's binary; that the log listener is handed
      Belle-SIP's lines on a phone is still to be seen. SDP `crypto` keys are redacted too.
      Linphone's own log (logcat, at *Every SIP message*) is not redacted.
- [ ] The SIP trace list with the raw message, copy and share; the logs screen. **Built 2026-11-11**: the list,
      the message, copy, and sharing one message as text or all of them as a file (a
      `FileProvider` of the feature's own). The logs screen is not built.
- [ ] The overview's network part (type, addresses, VPN, transport and ports in use) and the
      expiry countdown.
- [ ] Sharing a bundle (logs, trace, configuration without passwords) through `FileProvider`.

**Keypad**

- [ ] The split call button and the account picker; with one account, the round button.
- [ ] A field that takes paste, letters and full SIP URIs. A hardware keyboard types into it,
      Enter calls. **Built 2026-11-11**: a long press on the field pastes, a button swaps the dialpad for the
      system's keyboard, and the field's rules (`DialInput`) are tested. The layout with the
      keyboard up and the hardware keys are not yet seen on a device.
- [ ] Long press on 1 calls the account's voicemail number. **Built 2026-11-11**.
- [ ] The call icon on a suggestion, to call without filling the field first. **Built 2026-11-11**.

**Recents**

- [ ] The missed-calls badge on the tab. **Built 2026-11-11**: `TopLevelDestination.badge`, drawn by the shell.
- [ ] Filters for incoming, outgoing and each account; today All and Missed.
- [ ] Swipe to call back, and selecting several by long press.
- [ ] Sona's calls in the system's call history, and `ACTION_CALL_BACK` as a standing request.
- [ ] A record for a call refused by do not disturb, or as busy with call waiting off. **Built 2026-11-11**:
      the coordinator tells them (`refused`), and they are recorded as missed with the status the
      caller got. Under do not disturb the record is already seen, so no notification sounds.
- [ ] Codec and quality in the details.

**Call screen**

- [ ] *End and answer* beside *Hold and answer*.
- [ ] *Reconnecting…* when the network changes during a call.
- [ ] `CallActionProvider`, so the controls are contributions; *More* with call info.
- [ ] Haptics: connected, ended, failed.
- [ ] Landscape and tabletop layouts; nothing truncated at 200% font scale.
- [ ] The timer's format from the platform, not `"%d:%02d"`.

**The rest of the shell**

- [ ] About: version, Linphone's version, licenses, the link to the source (GPLv3 asks for it).
      **Built 2026-11-11** as a settings area (`SipEngine.version`, `AppInfo`). The source link is
      `SOURCE_URL` in `app/build.gradle.kts`, set to where the repository is expected to be: to
      correct once it exists. The licenses are a list kept by hand.
- [ ] `PartyActionProvider` for a contact, a history entry and a suggestion.
- [ ] List-detail on expanded windows: the strategy is in the shell, no destination declares a
      pane. The keypad as a supporting pane.
- [ ] `tel:` and `sip:` intents, as standing requests.
- [ ] The fast scroller in Contacts.

### 2 · Promised by phases 0 to 2

**To build**

- [ ] `NetworkMonitor` in `:calling:platform`, refreshing registrations on a network change:
      `refreshRegistrations()` is called only by the editor's *Retry*. Linphone watches the network
      itself, which may be enough; see what turning Wi-Fi off and on does first.
- [ ] `CallMachine` with its table tests, or AGENTS.md and calling.md say what is true: the
      decisions are in `CallCoordinator`, with 16 scenario tests.
- [ ] Remote surfaces within 5 seconds: the coordinator's `EventsFor` puts no 4-second bound on
      the engine, and a hold the other side refused is not thrown back to Telecom.
- [ ] With *Always reachable* off, unregister a minute after leaving the app.
- [ ] A call in progress when the process dies is recorded at the next start.
- [ ] TLS against the lab: a TLS transport and certificate in `lab/`, and a way for Linphone to
      accept the lab's own certificate (verification and CA import are in phase 4; the phase 1
      "done when" needs one of them sooner).

**To check on a real phone, against the lab** (ticked: Pixel 9 Pro XL, Android 17, 2026-11-10)

- [x] Registering over UDP on the local network, with `ACCESS_LOCAL_NETWORK` asked at *Check and
      save*.
- [x] 600 with audio both ways (4301 packets received and 4300 sent in 87 seconds, 6 and 4 lost;
      the microphone not silenced; `CallService` as `phoneCall|microphone`), 486 as *Busy*, 199 as
      *Not found*.
- [x] Mute, hold and resume (`sendonly`, Telecom *on hold* then *active*), DTMF (`#` ended the echo
      test on the server), the server hanging up (*Call ended*).
- [x] The proximity sensor: the screen goes off on the earpiece, the lock is let go on the speaker.
- [x] Audio keeps flowing with the phone asleep and locked; waking it shows the call over the lock
      screen.
- [ ] Audio routes: the earpiece works. **The speaker does not** (see below). Wired and Bluetooth
      not tried.
- [ ] Rings full screen when locked: seen after a minute asleep (the screen came on with the call
      over the lock screen within 5 seconds), not after an hour, and with the phone's own do not
      disturb on, so whether it sounded is not known.
- [ ] The microphone foreground type when answering: right when answered on the call screen;
      answering from the notification not tried.
- [ ] The missed-call notification: posted with Sona in the background; its *Call back* not
      pressed.
- [ ] A Bluetooth headset answers, mutes and hangs up.
- [ ] Two phones calling each other; call waiting with two real calls.
- [ ] TCP; two accounts on two servers; Wi-Fi off and on; Wi-Fi to mobile data during a call.
- [ ] The whole call matrix below, on the `full` flavor (it was last run before the directory).
- [ ] Android 10 to 13, on emulators at least.

#### Found on a real phone

Pixel 9 Pro XL, Android 17, the Docker lab on the same Wi-Fi, 2026-11-10.

- [x] **The speaker button moves the route, not the sound.** Telecom's communication device
      becomes the speaker, the button and the proximity lock follow, and Linphone's playback stream
      stays on the earpiece (`dumpsys media.audio_flinger`: its `MMAP_PLAYBACK` thread keeps
      `AUDIO_DEVICE_OUT_EARPIECE`, with no new audio patch). Linphone opens its player on a device
      it chose; the engine needs a function that selects Linphone's `AudioDevice` when Telecom's
      endpoint changes, as calling.md says it has.
- [x] **A `Reason: Q.850;cause=0` is read as a SIP `400`.** A call the server ends that way (a
      hang-up requested from Asterisk's console, `channel originate` running out) shows *Call
      failed · 400 Error* after an answered call, and is recorded as *Declined* (`400 Error`), not
      *Missed*, when it was never answered. With `cause=16`, what a caller hanging up sends, both
      are right. The mapper takes Linphone's `errorInfo.protocolCode`, which here does not come
      from a SIP response.
- [x] **A call missed on a locked phone is marked seen at once** when Recents was the last tab
      open: as the call screen closes, `MainActivity` is resumed for a third of a second behind the
      lock screen, Recents says *Seen*, and the missed-call notification never stays.
- [x] **The call screen lets the phone sleep and lock** during a call, after the screen timeout (30
      seconds on this phone). The call goes on; the screen should stay on while it is shown.
  The four above were fixed and seen fixed on the same phone the same day: the playback stream
  moves to the speaker and back, a `cause=0` ending reads as *Missed* or *Call ended*, the missed
  call stays unseen with its notification, and the screen stays on through a call.
- [ ] The keep-alive notification does not appear once notifications are allowed: the service was
      started without the permission and does not post it again. **Built 2026-11-11**: the service is started
      again each time the app comes to the front, and posts its notification again; to see on the
      phone.
- [ ] Linphone still plays tones of its own (an end-of-call tone on the earpiece, with its volume
      workaround on the DTMF stream) and asks for `org.linphone.core.tools.service.CoreService`,
      which is not in the app, on every incoming call (a warning in the log).
- [ ] The keypad's hint says *Extension, number or SIP address*; only digits can be typed.
      **Built 2026-11-11** (see *Keypad*); to see on the phone.
- [x] Every call negotiated PCMU at 8 kHz, though the lab offers G.722 first. With the codec
      G.722 was off in Linphone: with the codec setting applied (2026-11-11) the offer is
      `opus, G722, PCMU, PCMA` and 600 uses G.722.

#### Seen on the phone, 2026-11-11

The same Pixel 9 Pro XL, the `full` debug build installed over the one of the day before, the
Docker lab on the same Wi-Fi. Of what was built that day:

- [x] The database went from schema 2 to 3 in place: the account and its 14 calls were kept.
- [x] The codec order reaches the SDP offer (`m=audio … 96 9 0 8`), and a call to 600 used G.722.
- [x] The SIP trace: Linphone's log listener is handed Belle-SIP's lines, so REGISTER, INVITE and
      their answers appear in both directions; the `Authorization` of the INVITE reads
      `realm, algorithm, username, uri, qop, response=[redacted]`.
- [x] The keypad: a hardware keyboard types an address into the field and Enter calls; the
      system's keyboard swaps in with the field and the call button above it. Fast typing lost
      characters there (an answer of the state to an earlier key replaced the text): fixed and
      seen fixed.
- [x] Call readiness: the chip's warning, the switcher's row, the checklist (five ready, battery
      not), and the battery fix opening the system's question. The other fixes, and a permission
      refused for good, were not tried: everything else was already allowed on this phone.
- [x] About says `liblinphone 5.5.39`; the keep-alive notification is there after the app comes
      back.

Later the same evening, by hand (pasting by long press, the long press on 1 calling `*97`, the
licenses and the source link) and against the lab:

- [x] A call refused by do not disturb is in Recents as *Missed* with `486 Busy Here`, and
      nothing rings or notifies. One refused as busy during a call, with call waiting off, also
      posts the missed-call notification, and the Recents tab shows its badge.
- [x] The phone on a suggestion calls at once. Sharing the whole trace writes the file (67 kB,
      too much for an intent's text) and opens the system's share sheet; in it only the server's
      challenges keep a nonce, every `Authorization` is redacted.
- [x] An account's own codecs: PCMA put first, the offer is `8 96 9 0`; with G.722 off for the
      account, the answer to an incoming call uses PCMU. Two things learned: **the order of an
      offer does not decide the codec with this Asterisk**, which answers in its own order (G.722
      first), so the calls to 600 use G.722 because it is now on, not because of its place; and
      **an answer follows the caller's order**, as Linphone does and SIP recommends, so an
      account's order only matters for the calls it makes, and what it turns off for all of them.
- [x] In the account editor only the switch itself answered a touch, not its row: fixed, and
      seen fixed.

Not tried there: an account's own DTMF method, the readiness step after a first account, the
other readiness fixes, *Use mobile data* off.

### 3 · Release

- [ ] A remote repository; CI seen green there.
- [ ] CI runs lint, `testFullDebugUnitTest` and `assembleFullDebug` too.
- [ ] Lint passes: `MANAGE_OWN_CALLS` is declared in `:calling:platform` and used in
      `:telecom:jetpack` (`JetpackSystemCalls.kt`, `registerAppWithTelecom` and `addCall`).
- [ ] A signing configuration, and the release APK run on a device: R8 passes, but Linphone's
      native bindings under it were never started. `proguard-rules.pro` is one comment.
- [ ] Device tests: the Room migration from 1 to 2, the device contacts query, Core-Telecom.
- [ ] Screens on the fake engine, with screenshots in light, dark and large font.
- [ ] Previews in large font and right-to-left for the components.
- [ ] A translation, if the course wants one: every string is English.

### 4 · Docs

- [ ] AGENTS.md, architecture.md and calling.md name as built what is not: `CallMachine`,
      `NetworkMonitor`, `PermissionHost`, `CallActionProvider`, `PartyActionProvider`,
      `ForegroundNeed` (it is `CallForeground`), a back stack inside
      `CallActivity`. Build each or mark it as planned.

Phases 4 and 5 are untouched, as planned.

## The Asterisk lab

`lab/` holds the Asterisk the app is tested against, as configuration files that run in Docker for
development or are copied to the course's server. It is part of the deliverable: the course's task
is the Asterisk instance, and the app proves it.

A sketch of what it holds (check the syntax against the Asterisk version you deploy):

```ini
; pjsip.conf
[transport-udp]
type=transport
protocol=udp
bind=0.0.0.0:5060

[transport-tcp]
type=transport
protocol=tcp
bind=0.0.0.0:5060

[lab-endpoint](!)
type=endpoint
context=lab
disallow=all
allow=g722,ulaw,alaw        ; add opus if codec_opus is installed
direct_media=no
rtp_symmetric=yes           ; phones behind NAT, the usual case on campus Wi-Fi
force_rport=yes
rewrite_contact=yes

[101](lab-endpoint)
auth=101
aors=101

[101]
type=auth
auth_type=userpass
username=101
password=change-me

[101]
type=aor
max_contacts=2
qualify_frequency=30
; …the same for 102 and 103
```

```ini
; extensions.conf
[lab]
exten => _1XX,1,Dial(PJSIP/${EXTEN},30)
 same => n,Hangup()
exten => 600,1,Answer()           ; echo test: hear yourself, check audio both ways
 same => n,Echo()
exten => 486,1,Busy(5)            ; always busy: checks the busy message
exten => *97,1,VoiceMailMain(${CALLERID(num)}@default)
; any other number: 404, which checks "no such extension"
```

### The call matrix

Run before each release, on a phone and an emulator, against the lab:

| Case | Expect |
|---|---|
| 101 calls 102, 102 answers, either hangs up | both sides hear each other; Recents on both |
| 101 calls 600 | own voice back after a moment |
| 101 calls 486 | "486 is busy" |
| 101 calls 199 | "There is no extension 199" |
| Wrong password | registration fails with "check the password", no call possible |
| 102 does not answer | "No answer" after the server's timeout; missed call on 102 |
| 102 is in a call, 101 calls | call waiting on 102; hold and answer works |
| Locked phone, app in background an hour | rings full screen |
| Bluetooth headset | answer, mute, hang up from the headset |
| Wi-Fi to mobile data during a call | *Reconnecting…*, then audio again or a clear failure |
| DTMF to `*97` | voicemail menu responds |
| UDP, TCP, TLS | each registers and calls |

## Testing

As Docucraft's [testing.md](../../Docucraft/docs/testing.md): decisions are pure and tested on the
JVM, device tests check only what the platform answers, ports are tested with fakes.

| Where | JVM | Device |
|---|---|---|
| `:calling:domain` | `CallMachine` tables (every event in every state), coordinator against fakes, `AddressResolver`, `EndReason` mapping | — |
| `:directory:*` | T9 matcher, grouping of recents, retention | Room migrations, device contacts query |
| `:sip:linphone` | `LinphoneMapper`, trace parsing | registering and calling the lab (an instrumented suite, run on demand) |
| `:telecom:jetpack` | — | adding, answering, holding and disconnecting calls with Core-Telecom |
| `:feature:*` | ViewModels against `FakeSipEngine` | screens on the fake engine; screenshots in light, dark, large font |
| `:core:*` | settings resolution and overrides, navigation, `BaseViewModel` | — |

`FakeSipEngine` is scripted (`fake.incomingCall(from = "101")`, `fake.remoteAnswers()`,
`fake.failRegistration(401)`), so a UI test reads as the scenario it checks.

## Risks

| Risk | Why it matters | What we do |
|---|---|---|
| Telecom and Linphone both route audio | silent calls, audio on the wrong device | first spike of phase 2; Telecom owns the route, Linphone follows |
| Background limits on services and the microphone | calls that ring but have no audio, or never ring | the same spike; microphone only after a user action; readiness checks |
| Manufacturers that kill background apps (Xiaomi, Huawei, some Samsung settings) | *Always reachable* is not | battery check in readiness, a help page per manufacturer, test on one such phone |
| Campus Wi-Fi: client isolation, SIP ALG, blocked UDP | registrations that work at home and not in class | TCP and TLS transports, `rtp_symmetric` on the server, the network test |
| Linphone's API changes between 5.x releases | broken builds on upgrade | pinned version, every Linphone type confined to `:sip:linphone`, its mapper tested |
| APK size (Linphone's AAR is about 60 MB with every ABI) | slow installs | ABI splits (`arm64-v8a`, `armeabi-v7a` for older 32-bit phones, `x86_64` for emulators) plus a universal APK, an App Bundle for any store |
| GPLv3 (Linphone SDK) | the app's source must be available to its users | Sona is GPLv3 like Docucraft and Metadator; the source link is in About |
