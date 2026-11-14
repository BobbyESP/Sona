# AGENTS.md

Sona is a SIP softphone for Android, made first for Asterisk: it registers SIP accounts, places and
receives audio calls through Android's Telecom, keeps a history and a directory of contacts, and
explains every failure in words and in SIP. Nothing leaves the device except SIP and RTP to the
servers the user configured.

This file is the map and the rules. How each part works, and why, is in [`docs/`](docs/README.md).

**Status:** phases 0, 1 and 2 are built, and phase 3 is built except the parts its section lists (see [docs/roadmap.md](docs/roadmap.md)). The modules below that do not
exist yet arrive with the phase that needs them, and are added to `settings.gradle.kts` then.

---

## 1. Modules

| Module | What it is | Rule |
|---|---|---|
| `:app` | `App`, `MainActivity`, `CallActivity`, the navigation shell, DI wiring, flavors (`full`, `demo`) | The only module that names implementations |
| `:core:model`, `:core:common` | Ids, `SipAddress`, setting declarations, dispatchers, `StringProvider` | Plain Kotlin |
| `:core:data`, `:core:database` | DataStore settings; Room (accounts, overrides, history, contacts) | Seen only by `:app` and `*:data` |
| `:core:designsystem` | `SonaTheme`, tokens, blur, shared components | No feature knowledge |
| `:core:ui` | `BaseViewModel`, messages, contribution point contracts (`NavSection`, `TopLevelDestination`, `SettingsContributor`, `ReadinessCheck`) | |
| `:core:navigation` | Keys, `Navigator`, scenes, `NavigationMotion` | Cross-feature keys live here, uuids and ids at most |
| `:accounts:domain` / `:data` / `:ui` | SIP accounts on the user's own server: model, validation, use cases, the engine sync; Room storage; rows, state words, failure sentences | `:domain` plain Kotlin; `:data` only seen by `:app` |
| `:calling:domain` | `CallCoordinator` (the call decisions, tested by scenarios), call use cases, `AddressResolver`, `EndReason` | Plain Kotlin |
| `:calling:ui` | What the calling domain says in words: why nothing was dialed (more arrives with the quality badge) | |
| `:calling:platform` | `CallService`, `KeepAliveService`, notifications, boot receiver | Only `:app` depends on it |
| `:directory:domain` / `:data` / `:ui` | History, contacts, favorites, T9 matching | `:data` only seen by `:app` |
| `:sip:api` / `:sip:linphone` / `:sip:fake` | The SIP engine contract and its implementations | No `org.linphone` type leaves `:sip:linphone` |
| `:telecom:api` / `:telecom:jetpack` | Android Telecom (Core-Telecom) | No `androidx.core.telecom` type leaves `:telecom:jetpack` |
| `:credentials:api` / `:credentials:keystore` | SIP passwords, sealed with a Keystore key | |
| `:feature:*` | `onboarding`, `accounts`, `dialer`, `call`, `history`, `contacts`, `settings`, `diagnostics` | `:core:*`, `:calling:*`, `:directory:*` (never `:data`, `:database`, `:platform`) and `*:api` only. `sona.android.feature` fails the build otherwise |

Build setup: `build-logic` holds the convention plugins and `ProjectConfig` (minSdk 26,
compileSdk 37, targetSdk 37, Java 17). Versions are in `gradle/libs.versions.toml`.

## 2. Rules

### Calls (read [docs/calling.md](docs/calling.md) before touching the call path)

- **Only `CallCoordinator` changes a call.** Screens send it intents; no ViewModel, service or
  receiver calls the engine's `CallControls` or Telecom directly.
- **Call decisions live in the coordinator, each with a scenario test** (`CallCoordinatorTest`:
  the fake engine and the recording system play it out). Never an `if` in a service or a screen.
- **Every request to Telecom is guarded.** Android refuses some (a self-managed call may only end
  as local, remote, missed or rejected); a refusal is logged and the call goes on.
- **The Linphone `Core` is touched only on its own thread**, inside `:sip:linphone`.
- **Telecom owns audio routing.** Never call `AudioManager.setCommunicationDevice` or
  `startBluetoothSco`.
- **The 5-second rules**: a call's notification is posted within 5 seconds of adding the call to
  Telecom; a remote surface's request (headset, watch) is carried out within 5 seconds.
- **Never start `CallService` from `BOOT_COMPLETED`** (Android 15 forbids `phoneCall` there); boot
  starts `KeepAliveService` only.
- **Accounts are declared to the engine as a whole** (`setAccounts`). Linphone keeps no state the
  app depends on, and its auth infos are rebuilt whole on every change: removing one by object can
  miss Linphone's copy and keep an old password.
- **No Linphone cloud.** No sip.linphone.org accounts, no push, no remote provisioning: accounts
  are the user's own extensions, on the phone only.
- **Local network access is asked for in context** (Android 17, `ACCESS_LOCAL_NETWORK`), when an
  account is checked; a refusal is explained, never a silent failure.
- **Passwords live only in the vault.** Not in Room, DataStore, logs, traces, exports (unless the
  user confirms) or crash reports. Traces redact `Authorization` headers.

### Layers

- `*:api`, `:core:model`, `:core:common` and the `:domain` modules are plain Kotlin.
- Business logic is a use case in a `:domain` module; framework work goes behind a port.
- What the user or the network can cause is a sealed result (`DialOutcome`, `RegistrationState`,
  `EndReason`), with a sentence and a technical line.
- Test a port with a fake (`:sip:fake`, `FakeSystemCalls`), never a mock of the framework.

### UI

- Every screen's ViewModel extends `BaseViewModel`; it never holds a `Context` and never owns a call.
- Colors, type, shapes and motion come from `MaterialTheme`; no `Color(0x…)` outside the theme.
- A destination never measures the window; it reads `LocalPaneContext` / `LocalOverlayContext`.
- Blur only through `:core:designsystem`'s `blur` modifiers (`frosted`, `blurSource`, `outOfFocus`,
  `blurHalo`), which follow the device's `BlurLevel`; no other module names Haze.
- One destination, one entry, one back press. No `NavDisplay` inside a sheet or a dialog.
- Every string is a resource. Every failure shown has its technical line for *Show SIP details*.
- A screen never names another feature: it shows contributions.

## 3. Adding things

### A feature

1. Create `:feature:<name>` with `id("sona.android.feature")`; add it to `settings.gradle.kts`.
2. Keys other features open go in `:core:navigation/keys/`; keys only it opens stay in the feature.
3. Register its destinations in a `<name>Section` object (`NavSection`), bound in its Koin module
   (`single { HistorySection } bind NavSection::class`). The shell collects every section and tab;
   there is no list to edit.
4. Contribute what it adds to other screens: `TopLevelDestination`, `SettingsContributor`, `ReadinessCheck`
   (`CallActionProvider` and `PartyActionProvider` are planned, see docs/todo.md).
5. Bind its ViewModels and contributions in its Koin module; add the module to `App.kt`.
6. If it needs the engine to do something new, add a capability (below) first.

No existing feature should change. If one has to, a contribution point is missing: add it to
`:core:ui` instead.

### A setting

1. Declare it in its area's object in `:core:model` (`Setting.Toggle("calls.x", default = false)`,
   `Setting.Number`, `Setting.Text`, `Setting.Span`, `Setting.choice`), with `overridable = true` if
   accounts may override it. Never rename a released key.
2. If the engine needs it, map it in `EngineConfigBuilder` (`:calling:domain`) and apply it in
   `LinphoneConfigurer`.
3. Add its item to the area's `SettingsContributor`, with keywords for search and an icon (a
   contributor sharing an area with another names its `section`).
4. Add it to the catalogue in [docs/ux.md](docs/ux.md#the-options-catalogue).

### An engine capability

1. Add the `Capability` and the port functions to `:sip:api`.
2. Implement them in `:sip:linphone` (mapper first, tested) and `:sip:fake` (scriptable).
3. Add the events and decisions to `CallCoordinator` if calls are involved, with a scenario test in `CallCoordinatorTest`.
4. Contributions read the capability in their `Availability`.

## 4. Build and test

```bash
./gradlew assembleFullDebug                      # the app on Linphone
./gradlew assembleDemoDebug                      # the app on the fake engine, no server needed
./gradlew testDebugUnitTest testDemoDebugUnitTest testFullDebugUnitTest   # every JVM test
./gradlew connectedDebugAndroidTest              # device tests (the Keystore vault)
./gradlew spotlessApply                          # format and license headers
docker compose -f lab/compose.yaml up -d --build # the lab Asterisk; the emulator reaches it at 10.0.2.2
```

## 5. Docs

[architecture.md](docs/architecture.md) · [calling.md](docs/calling.md) · [ux.md](docs/ux.md) ·
[roadmap.md](docs/roadmap.md) · [todo.md](docs/todo.md) · [blur.md](docs/blur.md) · [lab/](lab/README.md)
