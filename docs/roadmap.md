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
| Messaging (SIP `MESSAGE`) | `:messaging:*`, `:feature:messaging` | `TopLevelDestination`, `PartyActionProvider` |
| Call recording | `:feature:recording` | `CallActionProvider` |
| Widgets and Quick Settings tiles (favorites, *Always reachable*, DND) | `:feature:widgets` | standing requests |
| AppFunctions ("call extension 102") | `:feature:appfunctions` | the same use cases |
| Real-time text | engine capability | `CallActionProvider` |

Then, once Sona is stable: the design system shared by Docucraft, Metadator and Sona as one
library.

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
| APK size (Linphone's AAR is about 60 MB with every ABI) | slow installs | ABI splits (`arm64-v8a`, `x86_64` for emulators), an App Bundle for any store |
| GPLv3 (Linphone SDK) | the app's source must be available to its users | Sona is GPLv3 like Docucraft and Metadator; the source link is in About |
