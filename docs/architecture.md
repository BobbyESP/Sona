# Architecture

How Sona is put together, and why. It follows the shape of Docucraft and Metadator on purpose:
core, engines (a contract and its implementations), shared catalogues, features. What is new here
is what a phone app needs that a document or tag editor does not: work that outlives every screen
(a call, a registration), a second activity for the lock screen, and **contribution points**, so a
feature added later (transfer, voicemail, messaging) plugs into screens it does not own.

The calling engine is in [calling.md](calling.md); screens and settings are in [ux.md](ux.md).

## Modules

```
:app  the shell ──────────┬──> :feature:onboarding, :accounts, :dialer, :call, :history,
  │                       │      :contacts, :settings, :diagnostics      (never another feature)
  │                       ├──> :calling:ui    ──> :calling:domain    (plain Kotlin)
  │                       ├──> :directory:ui  ──> :directory:domain  (plain Kotlin)
  │                       ├──> :directory:data ──> :directory:domain, :core:data
  │                       ├──> :calling:platform  (services, notifications, the call activity's host)
  │                       └──> :core:ui ──> :core:navigation ──> :core:designsystem ──> :core:model
  │
  └──> engines, bound only here:  :sip:linphone, :sip:fake      ──> :sip:api
                                  :telecom:jetpack              ──> :telecom:api
                                  :credentials:keystore         ──> :credentials:api
```

| Module | What it is | Rule |
|---|---|---|
| `:app` | `App`, `MainActivity`, `CallActivity`, the navigation shell, DI wiring, flavors | The only module that names implementations |
| `:core:model` | `AccountId`, `CallId`, `SipAddress`, `Party`, setting keys, value types | Plain Kotlin |
| `:core:common` | Dispatchers, `StringProvider`, `Clock`, text helpers | Plain Kotlin |
| `:core:data` | DataStore settings, Android strings | |
| `:core:database` | Room: accounts, per-account overrides, call history, local contacts, favorites | Only `:app` and `*:data` see it |
| `:core:designsystem` | `SonaTheme`, tokens, blur, shared components (dialpad, call buttons, avatars) | No feature knowledge |
| `:core:ui` | `BaseViewModel`, messages, the contribution point contracts | |
| `:core:navigation` | Keys, `Navigator`, scenes, `NavigationMotion` | Every cross-feature `NavKey` lives here |
| `:core:testing` | Test dispatchers, fixtures | Test-only |
| `:accounts:domain` / `:accounts:data` / `:accounts:ui` | The user's SIP accounts: model, validation, use cases, `AccountEngineSync`; Room storage; rows, state words, failure sentences | `:domain` plain Kotlin; `:data` only seen by `:app` |
| `:calling:domain` | `CallCoordinator`, the call state machine, call use cases, `AddressResolver`, `EndReason` mapping | Plain Kotlin |
| `:calling:ui` | The ongoing-call banner, quality badge, call-state labels | Shared by dialer, history, contacts, call |
| `:calling:platform` | `CallService`, `KeepAliveService`, `CallNotifications`, boot receiver, network monitor | Android components; only `:app` depends on it |
| `:directory:domain` | `Contact`, `CallRecord`, `DirectoryQuery`, the T9 matcher, ports | Plain Kotlin |
| `:directory:data` | History and local contacts on Room, device contacts on `ContactsContract` | Only `:app` depends on it |
| `:directory:ui` | `ContactRow`, `CallRecordRow`, `Avatar` | |
| `:sip:api` | The SIP contract: `SipEngine`, `AccountRegistry`, `CallControls`, `EngineConfig`, capabilities | Plain Kotlin |
| `:sip:linphone` | liblinphone behind the contract | Linphone is `implementation`: no `org.linphone` type leaves the module |
| `:sip:fake` | A scripted, in-memory engine | Previews, UI tests, screenshots, the `demo` flavor |
| `:telecom:api` / `:telecom:jetpack` | Registering calls with Android, audio endpoints, remote surfaces | Core-Telecom is `implementation` |
| `:credentials:api` / `:credentials:keystore` | SIP passwords, sealed with an Android Keystore key | Passwords never enter Room or DataStore in clear |
| `:feature:*` | What the user sees | `:core:*` (not `:data`/`:database`), `:accounts:*`, `:calling:*` and `:directory:*` (not `:data`/`:platform`), `*:api`; tests may also use `*:fake` |

**What the graph enforces, and why:**

- **Domains are plain Kotlin.** `:core:model`, `:calling:domain`, `:directory:domain` and every
  `*:api` are JVM modules, so the call state machine, address resolution and T9 matching are tested
  in seconds without a device. An address is a `SipAddress` value class, never an `android.net.Uri`.
- **Screens do not reach storage or engines.** A feature sees the contracts; the app binds them.
- **Features do not know each other.** They open one another by keys in `:core:navigation`, and add
  to one another's screens through contribution points (below).
- **The build refuses what breaks these rules.** `sona.android.feature` checks a feature's project
  dependencies against an allow list, as Docucraft's convention does. A new module is refused until
  someone decides a feature may see it.
- **Engines are replaceable in one line of DI.** Linphone for PJSIP, the jetpack Telecom adapter for
  a hand-written `ConnectionService`, the Keystore for a hardware-backed vault: each is one binding
  in `:app`. The fake engine proves it daily, since the `demo` flavor and every UI test run on it.

The build logic is the included build `build-logic`, one convention plugin per kind of module:
`sona.jvm.library`, `sona.android.library`, `sona.android.compose`, `sona.android.feature`,
`sona.android.room`, `sona.android.application`. `ProjectConfig` holds minSdk 26, compileSdk 37 and
Java 17.

**Why minSdk 26.** Core-Telecom needs 24 and Linphone less; 26 is where notification channels,
`java.time` without desugaring of the basics, and adaptive icons all exist, and it matches Metadator.
Nobody in a lab has a phone older than Android 8.

## Layers inside a feature

```
presentation   screen ─ intents ─> ViewModel ─ state/effects ─> screen
                                       │
domain         use cases ─ ports (interfaces) ─ models          plain Kotlin
                                       │
data           port implementations: Room, DataStore, Linphone, Telecom, ContactsContract
```

- **Anything the framework does goes behind a port**: `SipEngine`, `SystemCalls`, `CredentialVault`,
  `DeviceContacts`, `RingtonePlayer`, `BatteryOptimizations`, `NetworkMonitor`, `CameraPermission`, `VideoSurfaces`. Tests use fakes.
- **A port that needs an `Activity`** (asking for a permission, opening the full-screen-intent
  settings) is called by the screen, after the ViewModel emits an effect.

### Results, not exceptions

What the user or the network can cause is a sealed result the UI can explain:
`DialOutcome` (no account, account not registered, invalid address, no microphone permission,
offline), `RegistrationState` (`Failed(reason)` with the SIP status), `EndReason` (busy, declined,
not found, unauthorized, unreachable, failed with a code). A generic `Result<T>` would put "the
extension is busy" in the same bucket as "the engine crashed".

**Every failure has two texts.** A sentence for anyone ("Extension 102 is busy") and the technical
detail ("486 Busy Here, via udp/192.168.1.10:5060"), shown under it when *Show SIP details* is on.
For a course on Asterisk, the second text is half the value of the app.

## ViewModels (MVI)

Every screen's ViewModel extends `BaseViewModel<Intent, State, Effect>` from `:core:ui`, copied from
Docucraft: one `StateFlow` state, `effects` dropped when nobody listens, `messages` kept until shown.
ViewModels never hold a `Context`. What survives process death goes in the entry's
`SavedStateHandle`.

**A ViewModel never owns a call.** Calls and registrations live in `CallCoordinator`, a singleton in
the application scope. Screens observe it and send it intents. A rotation, a back press or the
process going to the background does not touch a call.

## Contribution points

The rule that features do not know each other is cheap for navigation (keys) but not for screens
that show other features' actions: the call screen has a *Transfer* button, a contact has *Message*,
the settings list has a section per feature. If the call feature had to know transfer, every new
feature would edit old ones. Instead each such screen declares a contract in `:core:ui` (or
`:calling:ui`), and features contribute to it through Koin (`getAll<T>()`), sorted by an `order`:

| Contract | Screen that shows it | Contributed today by | Later by |
|---|---|---|---|
| `NavSection` | the `NavDisplay` | every feature | every feature |
| `TopLevelDestination` | navigation bar / rail | dialer, history, contacts, messages | voicemail |
| `CallActionProvider` (planned) | the call screen's controls | call (mute, hold, keypad, audio route) | transfer, add call, record, chat |
| `PartyActionProvider` | a contact's numbers, a history entry, a conversation's top bar (`PartyActionButtons`, fed by the shell's `LocalPartyActionProviders`) | messages (*Message*) | calls (video call), presence (watch), a dialer suggestion |
| `SettingsContributor` | settings root and settings search | every feature with options | every feature with options |
| `ReadinessCheck` | onboarding and *Call readiness* | calling (mic, notifications, full-screen intents, battery, registration) | — |

A contribution is data plus a composable slot at most: an id, an order, a label, an icon, a
`Flow<Availability>` (hidden, disabled with a reason, enabled) and what happens when it is used,
usually a `NavKey`. The screen lays them out; it never names the feature.

**Adding a feature is then:** a module with `sona.android.feature`, its keys, its `NavSection`,
its contributions and a Koin module, and one line in `App.kt`. No other feature changes. The
checklist is in [AGENTS.md](../AGENTS.md#adding-things).

## Capabilities

Not every engine, account or server can do everything: the fake engine has no TLS, an account may
have encryption off, a server may not answer `MESSAGE`. `SipEngine.capabilities` is a set
(`AudioCalls`, `Dtmf`, `Hold`, `BlindTransfer`, `AttendedTransfer`, `Conference`,
`Messaging`, `Presence`, `Mwi`, `Srtp`, `Zrtp`, `CallStats`), and contributions read it in their
`Availability`. A button whose capability is missing is not shown; one whose capability exists but
is off for this account is shown disabled, with the reason and a way to turn it on.

## Settings

Sona will have many options, so adding one has to cost a line, not a method on a repository.
Docucraft's `SettingsRepository` grows a method per option; Sona declares options instead:

```kotlin
// :core:model, one object per area
object CallSettings {
    val AutoAnswer = Setting.Toggle("calls.auto_answer", default = false)
    val AutoAnswerDelay = Setting.Span("calls.auto_answer_delay", default = 3.seconds)
    val IncomingStyle = Setting.choice("calls.incoming_style", IncomingStyle.FullScreen)
}

interface SettingsStore {                       // :core:model port, DataStore in :core:data
    fun <T> observe(setting: Setting<T>): Flow<T>
    suspend fun <T> set(setting: Setting<T>, value: T)
}
```

- **A setting is declared once**: key, default, serializer, and whether it can be overridden per
  account. Storage, reading, defaults and export come from the declaration.
- **Per-account overrides are `Override<T>`** (`Inherit` or `Value(T)`), stored with the account in
  Room. `EffectiveSettings` resolves account over app over default, so codecs, transport,
  encryption and DTMF mode can differ by account and the UI says "Uses app default (Opus, G.722)".
- **Settings screens are built from items bound to settings**: `switch(CallSettings.AutoAnswer)`,
  `choice(...)`, `slider(...)`, `reorderable(...)` for codecs. Each item also feeds the **settings
  search index** (title, summary, keywords, destination), so search over a hundred options is free.
- **What the engine needs is one `EngineConfig`**, built by `:calling:domain` from the effective
  settings and pushed to the engine whenever it changes. The engine never reads settings, and an
  option that needs a new registration or only applies to the next call says so under its switch.
- **Export and import** (JSON, passwords optional and only with confirmation) come from the
  declarations too, for moving a lab setup between phones.

## Dependency injection

Koin, started in `App.kt`, as in both other apps. Each feature binds its ViewModels and
contributions in its own module, the catalogues bind their ports in their `:data` modules, and the
app binds the engines:

```kotlin
val engineModule = module {
    single<SipEngine> { LinphoneSipEngine(get(), get()) }   // or FakeSipEngine in the demo flavor
    single<SystemCalls> { JetpackSystemCalls(get()) }
    single<CredentialVault> { KeystoreCredentialVault(get()) }
}
```

**Swapping an implementation is always one binding.** If a swap needs more, the design is not
finished.

## Navigation

One back stack per activity, one Navigation 3 `SonaNavDisplay`, typed `@Serializable` keys,
`Navigator` (`goTo`, `goBack`, `goBackWhile`, `removeDestination`, plus `switchTopLevel`), scene
strategies tried in order (overlays first, then list-detail), destinations told whether they share
the window (`LocalPaneContext`), never measuring it. All of this is Docucraft's
[navigation.md](../../Docucraft/docs/navigation.md), carried over with its rules: one destination,
one entry, one back press; never a `NavDisplay` inside a sheet.

What is new:

- **Top-level destinations come from contributions.** The shell builds the bar or rail from
  `TopLevelDestination`s and the entries from `NavSection`s, all collected from Koin.
  `Navigator.switchTopLevel` shows a tab's root with Recents, the first tab, kept under it: back
  from another tab returns to Recents, back from Recents leaves the app. Tabs fade through
  (`TopLevelMotion`), as siblings; steps inside a tab slide. Keeping each tab's own deeper stack
  across switches is a later improvement, once tabs have detail screens.
- **Calls have their own activity.** `CallActivity` shows over the lock screen (`showWhenLocked`,
  `turnScreenOn`), in its own task, with its own back stack rooted at `OngoingCall` or
  `IncomingCall` and rendered by the same `SonaNavDisplay`. Back from a call never lands in the
  app's history; the ongoing-call banner and the notification lead back to it. See
  [calling.md](calling.md#the-call-activity).
- **Requests from outside the UI are standing requests**, as in Docucraft: `tel:` and `sip:`
  intents, the system dialer's *Call back* (`ACTION_CALL_BACK`), a notification's *Call back*, a
  widget later. The activity turns each into a request in a holder; the shell consumes it.

## Theme

`SonaTheme` is `MaterialExpressiveTheme` with one `MotionScheme.expressive()` instance and a scheme
from the wallpaper or a seed through MaterialKolor, built off the main thread and cached. Theme
transitions in steps, frosted surfaces, blur halos and out-of-focus backgrounds come from
Docucraft and Metadator. The rules in their architecture docs apply as written, except for blur's
API, below.

What is new:

- **An account can have a color**, and screens about one account nest `SonaAccentTheme` with it:
  the account editor, its diagnostics, and the call screen for a call on that account. With two
  accounts (the lab's Asterisk and a home PBX) the user always knows which line rings.
- **The call screen is its own theme context**: always dark-surfaced over the lock screen, tinted
  with the account's color, so it reads as "a call" at a glance.
- **Blur follows the device.** `SonaTheme` provides a `BlurLevel` (`Off`, `Reduced`, `Full`),
  worked out from the Android version, Android Go, the phone's memory, battery saver, its
  temperature and whether the system allows window blur, and updated as those change. Haze is
  configured from it (disabled at `Off`, its lowest input resolution at `Reduced`), so no screen
  checks the device. `Off` is below Android 12, on Android Go, in battery saver and when the phone
  is hot; `Reduced` keeps frosted surfaces only. The *Blur* setting (Appearance, from Android 12)
  can lower the level, never raise it. [blur.md](blur.md) has the details, and how to take the
  package to Docucraft and Metadator.
- **Blur has four calls, in `:core:designsystem/blur`**, and no other module names Haze:
  `rememberBlurSource()` and `Modifier.blurSource()` on the content that scrolls,
  `Modifier.frosted(source, containerColor, shape, amount = { … })` on the surface over it,
  `Modifier.outOfFocus { radius }` on content itself, and `Modifier.blurHalo` around a floating
  element. What animates is a lambda read while drawing: an animation redraws, never recomposes.
  Animate a frost's `amount`, not its color or radius: Haze builds its blur again for a new color or
  radius, and only fades it for a new amount.

**Sona copies the design system of the other two apps.** It is the third copy of `ThemeTransition`,
the blur defaults and the settings components. Extracting them into one library shared by the three
apps is worth doing once Sona's MVP ships, not before.

## Integrations

- **Linphone SDK** (`org.linphone:linphone-sdk-android`, from Belledonne's Maven repository, GPLv3
  like the app). See [calling.md](calling.md).
- **Core-Telecom** (`androidx.core:core-telecom` 1.1.0): system call integration, audio endpoints,
  Bluetooth and watch controls, unified call history.
- **FileProvider** for sharing logs and SIP traces.
- **No analytics in the MVP.** Firebase can come later behind `AnalyticsHelper`, as in Docucraft,
  off by default: a phone app that phones home needs a reason.
