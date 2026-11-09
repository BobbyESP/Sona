# Docs

| Document | What it covers |
|---|---|
| [architecture.md](architecture.md) | Modules and the rules the build enforces, layers, MVI, contribution points, capabilities, settings, DI, navigation, theme |
| [calling.md](calling.md) | The SIP engine contract and Linphone, the call coordinator and its state machine, Telecom, audio routing, services, readiness, notifications, the call activity |
| [ux.md](ux.md) | Principles, the map of destinations, every screen, components, motion, accessibility, and the full catalogue of options |
| [roadmap.md](roadmap.md) | Phases with what "done" means, the Asterisk lab and its call matrix, testing, risks |

The rules and the "how to add" checklists are in [AGENTS.md](../AGENTS.md).

## Decisions so far

| Decision | Chosen | Instead of | Why |
|---|---|---|---|
| SIP and media engine | Linphone SDK 5.5.39, behind `:sip:api` | PJSIP | Maven artifact, no native build, encryption and chat ready, GPLv3 like the app |
| System integration | Core-Telecom 1.1.0 | a hand-written `ConnectionService` | Headsets, watches, cars and the system's call history for free; one API from API 24 |
| Reachability | a keep-alive foreground service | push | Asterisk does not push |
| Architecture | Docucraft/Metadator's modules, plus contribution points | a single module | Features added later plug into screens they do not own |
| Settings | declared `Setting<T>`s with per-account overrides | a repository method per option | Many options, each a line; search and export for free |
| Local network | `ACCESS_LOCAL_NETWORK`, asked when an account is checked | asking at start | Android 17 blocks a lab's Asterisk without it; asked where the reason is obvious |
| First release | accounts, registration, audio calls, recents, contacts, keypad, MVP options | everything at once | The course's deadline; the rest has its place already |
