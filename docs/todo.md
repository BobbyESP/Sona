# What is left

The short list: what to do next, what is still to build, and what is built but not yet seen
working. The history of each item (what was found, where it was checked) stays in
[roadmap.md](roadmap.md#what-is-left); this file only says what remains. Last updated 2026-11-11.

Remove a line when it is built **and** seen working, and note where in the roadmap.

## Next, in this order

1. **Finish checking what is built** (the list [below](#built-not-yet-seen-working)). Most needs
   only the phone and the lab, and ten minutes. This is the gate to new features.
2. **The MVP options that are one switch each** (see [Options](#options)): they are what the
   phase 3 "done when" still lacks most visibly.
3. **The call screen's small things**: *End and answer*, the timer's format, haptics.
4. **A signed release APK run on a phone**: R8 passes, but Linphone under it was never started.
5. **Re-register on a network change**: see first what Wi-Fi off and on does.

Done on 2026-11-11: committed; remote at github.com/BobbyESP/Sona; CI green there (format, lint,
unit tests, both debug builds); `SOURCE_URL` correct.

## Still to build

### Call screen

- [ ] *End and answer* beside *Hold and answer*.
- [ ] *Reconnecting…* when the network changes during a call.
- [ ] The timer's format from the platform, not `"%d:%02d"`.
- [ ] Haptics: connected, ended, failed.
- [ ] Landscape and tabletop layouts; nothing truncated at 200% font scale.
- [ ] `CallActionProvider`, so the controls are contributions; *More* with call info.

### Keypad and Recents

- [ ] The split call button with the account picker (one account: the round button).
- [ ] Recents filters for incoming, outgoing and each account (today: All, Missed).
- [ ] Swipe to call back; selecting several by long press.
- [ ] Sona's calls in the phone app's history, and `ACTION_CALL_BACK`.
- [ ] Codec and quality in a call's details.

### Options

Each needs its declaration, its item with keywords, and whatever reads it.

- [ ] Calls: account for calls (default or ask), auto-answer (delay, speaker), full screen or
      banner when unlocked, answer by swipe, vibrate when ringing, the proximity sensor's switch.
- [ ] Audio: start calls on speaker, dialpad tones, the dialpad haptics' switch.
- [ ] Network: start on boot (it always does).
- [ ] Privacy: hide caller on the lock screen.
- [ ] Contacts and history: only contacts with an address or number, name and sort order.
- [ ] Appearance: high contrast, the seed color's picker, dialpad letters, addresses under names,
      app language.
- [ ] Diagnostics: *Show SIP details*, reset everything.
- [ ] Accounts: choosing the color, a ringtone per account.

### Diagnostics

- [ ] The logs screen.
- [ ] The overview's network part (type, addresses, VPN, transport, ports) and the expiry
      countdown.
- [ ] Sharing a bundle: logs, trace, configuration without passwords.
- [ ] Linphone's own log (logcat, at *Every SIP message*) still prints `Authorization` headers.

### Shell

- [ ] `tel:` and `sip:` links open Sona.
- [ ] `PartyActionProvider` for a contact, a history entry and a suggestion.
- [ ] List-detail on wide windows; the keypad as a supporting pane.
- [ ] The fast scroller in Contacts.
- [ ] The readiness step also after a first account added from the chip, not only the welcome.

### Under the surface

- [ ] Re-register on a network change (`NetworkMonitor`): first see what Wi-Fi off and on does,
      Linphone may already do it.
- [ ] A call in progress when the process dies is recorded at the next start.
- [ ] With *Always reachable* off, unregister a minute after leaving the app.
- [ ] Remote surfaces (headset, watch) answered within 5 seconds; a refused hold told to Telecom.
- [ ] TLS against the lab: a certificate in `lab/`, and a way for Linphone to accept it.
- [ ] Linphone plays tones of its own, and asks for a `CoreService` that is not in the app.
- [x] `CallMachine`: the docs now say the decisions are in `CallCoordinator`, scenario-tested.

## Built, not yet seen working

Built on 2026-11-11 and tested on the JVM where that is possible; these still need a phone. The
rest of that day's work was seen on the Pixel the same evening (see the roadmap).

- [ ] An account's own DTMF method; each DTMF method against `*97` (the lab's voicemail needs a
      mailbox for 102 first); echo cancellation off and on.
- [ ] *Use mobile data* off: registration drops on mobile data, returns on Wi-Fi.
- [ ] An SRTP key redacted in the trace (needs an encrypted call, phase 4).
- [ ] Call readiness: each check turning to a warning, each fix landing on the right system
      screen (only the battery one was pressed), a permission refused for good, the step after a
      first account, the rows' look.

## Never checked on a phone

From phases 1 and 2.

- [ ] Bluetooth and wired headsets: routes, and answer, mute, hang up from the headset.
- [ ] Two phones calling each other; call waiting with two real calls.
- [ ] TCP; two accounts on two servers; Wi-Fi off and on; Wi-Fi to mobile data during a call.
- [ ] Ringing full screen after an hour asleep, with the phone's own do not disturb off.
- [ ] Answering from the notification (the microphone foreground type); *Call back* on a missed
      call's notification.
- [ ] The whole [call matrix](roadmap.md#the-call-matrix) on the `full` flavor.
- [ ] Android 10 to 13, on emulators at least.

## Release

- [ ] A signing configuration; the release APK run on a device; `proguard-rules.pro` for Linphone.
- [ ] Device tests: the Room migrations (1 to 2, 2 to 3), the device contacts query, Core-Telecom.
- [ ] Screenshots of the screens on the fake engine: light, dark, large font.
- [ ] Previews in large font and right-to-left.
- [ ] A translation, if the course wants one.
- [ ] Before Google Play only: the battery exemption request against Play's policy.

## Docs

- [ ] architecture.md and calling.md may still name a back stack inside `CallActivity` as built;
      check. (`CallMachine`, `PermissionHost`, `ForegroundNeed` fixed 2026-11-11; the two
      providers and `NetworkMonitor` are marked planned.)
- [ ] The options catalogue in [ux.md](ux.md#the-options-catalogue) has no row for the SIP trace
      screen's share, nor says which options exist yet.

Phases 4 and 5 of the roadmap are untouched, as planned.
