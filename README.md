# Sona

**A small SIP phone for Android. No account with us, no cloud, no tracking: you bring your own
server, Sona makes the calls.**

Sona lets you use an extension from your own phone system (an Asterisk, a FreePBX, any SIP server)
as if it were a normal phone line. You type the server, the extension and the password, and you can
call and be called, with the ringing screen over the lock screen, the history, the contacts and the
Bluetooth headset, like any other call on your phone.

That is all it is, and that is the point.

## Why it exists

Most SIP apps for Android started as something else: a platform with its own accounts, a push
service you have to trust, a chat app with a phone bolted on, or a settings screen with two hundred
switches and no explanation. If all you want is to use *extension 102 on the office server* from
your pocket, that is a lot to carry.

Sona was made for that one job:

- **Lightweight.** It does calls, history and contacts. Nothing else is in the way.
- **No accounts.** There is nothing to sign up for. No Sona account, no Linphone account, no
  e-mail. An account in Sona is *your* extension on *your* server, and it lives only on the phone.
- **Made first for Asterisk.** Adding an extension takes three fields: server, extension and
  password. Transport, port and domain are worked out for you, and everything else is one level down
  when you need it.
- **It explains itself.** When a call or a registration fails, Sona says what happened in plain words
  ("The server refused the password"; "102 is busy"; "There is no extension 199") and, under it, the
  SIP status and server for whoever is setting up the other side.
- **Private by construction.** The only traffic that leaves the phone is SIP and RTP to the servers
  you configured. No analytics, no remote provisioning, no push provider. Passwords are sealed with
  a key in the Android Keystore and never written to the database, logs or traces.

## What you can do with it

- **Register one or several SIP accounts** (UDP, TCP or TLS). Saving an account *tests it first* and
  tells you the result in words.
- **Make and receive audio calls** that Android treats as real calls, through Telecom: a full-screen
  ringing screen over the lock screen, headsets, Bluetooth and watches work as they should.
- **Stay reachable.** With *Always reachable* on, a small foreground service keeps your extension
  registered, since Asterisk does not push. A readiness checklist tells you if something on the phone
  (permissions, battery optimization, a muted channel) would stop a call from ringing.
- **Call controls**: mute, hold, speaker and audio routes, keypad tones (DTMF), call waiting, and a
  proximity sensor that behaves.
- **Recents**: calls grouped by day, missed calls with a badge and a *Call back* notification, and
  the SIP status of each call.
- **Contacts**: your phone's contacts (read only, asked for in context) and Sona's own, for the
  extensions nobody puts in a phone book. Favorites included.
- **A keypad with suggestions**: type `5646` and find "John".
- **Do not disturb** that refuses calls as busy or declined, per your choice.
- **Codec order and per-account overrides**, DTMF method, echo cancellation, Wi-Fi only.
- **A SIP trace** you can read, copy and share, with credentials redacted, for when the other side
  of the line is the problem.
- **Material 3 Expressive** look, dynamic color, light and dark, a blur that follows your device, and
  English and Spanish.

## Who it is for

- People who run their own Asterisk, FreePBX or similar, at home, in a small office or a lab.
- Students and teachers building a VoIP course or a PBX, who need a client that shows what is
  happening instead of hiding it.
- Anyone who wants a softphone that does not ask who they are.

If you want video, chat, presence or a hosted service, Sona is not that (yet; see below).

## Status

Sona is in **beta (1.0.0-beta.1)**. Registering, calling, receiving, history, contacts, the keypad,
settings and diagnostics are built and have been run on a real phone against an Asterisk. Some
options and polish are still on the list, and a few things have not yet been tried on all kinds of
hardware (Bluetooth headsets, older Android versions, every network change).

What is built and what is left is written down honestly in [docs/todo.md](docs/todo.md) and
[docs/roadmap.md](docs/roadmap.md).

It needs **Android 8.0 (API 26) or later**. Sona's own SIP stack is
[Linphone SDK](https://gitlab.linphone.org/BC/public/linphone-sdk) used as a library only: nothing
connects to Linphone's services.

## Try it

You need a SIP server. If you do not have one, the repository includes a ready-to-run Asterisk with
three extensions, an echo test (`600`), an always-busy number (`486`) and voicemail (`*97`):

```bash
docker compose -f lab/compose.yaml up -d --build
```

Then, in Sona, *Add account › Asterisk extension*: the server's address (`10.0.2.2` from the Android
emulator, your computer's address from a real phone), extension `102` and its password. More in
[lab/README.md](lab/README.md).

Prefer to look around without a server? The `demo` flavor runs on a fake engine that plays calls by
itself.

## Build it

```bash
./gradlew assembleFullDebug   # the real app, on Linphone
./gradlew assembleDemoDebug   # the demo, on the fake engine, no server needed
```

Needs JDK 17 and the Android SDK. Tests, formatting and the rest of the commands are in
[AGENTS.md](AGENTS.md#4-build-and-test). Signed releases are built in the open by GitHub Actions;
see [docs/releasing.md](docs/releasing.md).

## How it is made

Kotlin, Jetpack Compose, Material 3 Expressive, Room, DataStore, Koin, Android Core-Telecom and
Linphone SDK. The code is split into small modules with rules the build enforces: all call
decisions live in one tested place, and the SIP engine, Telecom and the password vault each sit
behind their own interface.

| Read | To learn |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Modules, layers, how features plug in |
| [docs/calling.md](docs/calling.md) | The call path, Telecom, audio, services |
| [docs/ux.md](docs/ux.md) | Every screen, and every option |
| [AGENTS.md](AGENTS.md) | The rules for contributing, and how to add a feature or a setting |

## License

Sona is free software under the [GNU GPLv3](LICENSE), like the Linphone SDK it builds on. The source
is yours to read, change and share.
