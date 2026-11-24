# UX

What the user sees and can change: the principles, the map of destinations, each screen, the
components, motion, and the catalogue of options. Implementation rules for theme and navigation
are in [architecture.md](architecture.md); this document is about what they are used for.

## Principles

1. **A call is never in doubt.** Whether Sona can ring, which account a call is on, whether the
   other side hears you, why a call failed: always visible, in words, never only a color.
2. **Simple first, everything reachable.** The default path is the fewest steps (an Asterisk
   extension is a server, a number and a password). Every SIP option exists, one level down, and
   settings search finds any of them by name.
3. **Explain, then show the detail.** Every failure says what happened in a sentence, and the SIP
   status, server and transport under it for whoever wants them (*Show SIP details*).
4. **Hands and pockets.** Large targets for answering and hanging up, nothing destructive within
   reach of an accidental touch during a call, haptics on what matters.
5. **Expressive where it means something.** Shape morphs, springs and color are used for state
   changes the user caused or must notice (ringing, connecting, muted, on hold), not as decoration.

## Map

```
MainActivity
├── Recents (top level)         list ─> CallDetail ─> (call, add to contacts, copy address)
├── Contacts (top level)        list ─> ContactDetail ─> ContactEditor
├── Keypad (top level)          keypad + suggestions ─> AccountPicker (overlay)
├── AccountSwitcher (overlay, from the account chip in every top bar)
│     └── Accounts ─> AccountEditor ─> AccountDiagnostics
├── Search (from the search bar on Recents and Contacts)
├── Settings ─> area screens, Call readiness, Import/Export, About
├── Diagnostics ─> SIP trace, Logs, Network test
└── Onboarding (first start): Welcome ─> Add account ─> Call readiness ─> Done

CallActivity (own task, over the lock screen)
├── IncomingCall
└── OngoingCall ─> Keypad (DTMF), AudioRoutes (overlay), CallInfo (overlay), ended summary
```

- **Navigation bar on compact windows, navigation rail from medium width**, built from the
  `TopLevelDestination` contributions, with badges (missed calls on Recents). M3 Expressive's short
  navigation bar and wide navigation rail, collapsed by default.
- **The account chip is in every top bar.** It shows the default account's name, color and
  registration state (a dot and a word: *Registered*, *Registering…*, *Offline*, *Failed*), and opens
  the account switcher. With a readiness problem, the chip turns to a warning and the switcher
  starts with it.
- **On expanded windows** Recents and Contacts are list-detail, and the keypad becomes a supporting
  pane beside them, so a tablet on a desk is a desk phone.

## Screens

### Onboarding

Three steps, skippable only where nothing is lost:

1. **Welcome**: what Sona is, one sentence about Asterisk and SIP, *Add an account*.
2. **Add account**, three ways, the first one the default:
   - *Asterisk extension*: server, extension, password. Domain, transport and port are derived
     (UDP 5060, changeable under *More options*).
   - *Other SIP account*: every field, grouped (identity, server, authentication).
   - *Scan a code* (later phase): a QR code with the account, made by the lab's admin.

   **Saving tests the account first.** The button reads *Check and save*, registers once, and shows
   the result in words: "Registered as 102 on 192.168.1.10" or "The server refused the password
   (401)". A failed account can still be saved, as disabled.
3. **Call readiness**: the checklist from [calling.md](calling.md#call-readiness), each item with
   its fix, then *Always reachable* explained with its notification shown as a preview.

### Recents

Calls grouped by day, consecutive calls with the same party collapsed into one row with a count.
Each row: avatar, name or address, direction icon and color for missed, account color stripe when
there are several accounts, time; tapping calls back is a setting (*Tap to call back* or *Tap for
details*). Filter chips above: All, Missed, Incoming, Outgoing, and one per account. Swipe to call
back or delete (both directions configurable), long press to select several.

**CallDetail**: every attempt with that party, each with its duration and how it ended (both texts),
codec and quality summary, and the actions contributed by features (`PartyActionProvider`): call,
copy address, add to contacts, and later message.

### Contacts

Two sources, shown together and marked: the device's contacts (with a SIP address or phone number,
read with permission, never written) and Sona's own contacts (for extensions nobody puts in a phone
book: "Lab 103 · Printer room"). Favorites on top as a row of large avatars. Fast scroller with
letters. ContactDetail lists each address with its call action and the account it would use.

### Keypad

The reference screen for the app's look (see [The expressive language](#the-expressive-language)).

```
┌──────────────────────────────┐
│ Keypad                       │
│                              │
│            102               │  numerals.large, a spring at each key
│     ( @ sip:102@lab.local )  │  tonal pill; the failure in an error pill
│   ( (A) Ana · 102  (📞) )     │  suggestion pills
│   (  1  ) (  2  ) (  3  )    │
│   (  4  ) (  5  ) (  6  )    │  pills that squash and square when held
│   (  7  ) (  8  ) (  9  )    │
│   (  *  ) (  0  ) (  #  )    │  * and # on the secondary container
│   [⌨]   (    📞    )   [⌫]   │  tonal, primary, tonal
└──────────────────────────────┘
```

- **The dialpad** (`Dialpad`): 12 keys with letters (optional), each a pill that squashes into a
  rounded square under the finger and springs back, with a DTMF tone and a haptic tick (both
  optional). Long press `0` for `+`, `1` for voicemail (marked under the 1 when there is one), `*`
  and `#` as themselves. Keys too short for two lines (landscape) show the digit alone.
- **The number** is the loudest text on the screen; under it, the resolved address in a pill
  ("sip:102@lab.local"), or why the last call failed, in an error pill read by TalkBack. A long press
  on the number pastes, and full SIP URIs are kept.
- **The action row**: the keyboard switch (a toggle, square when on), the call button, delete (long
  press clears). The keyboard switch swaps the dialpad, which springs out, for the system keyboard.
- **Suggestions while typing**: T9 and plain matches over contacts and recents, best first, so
  typing `5646` finds "John". Tap one to fill, the call icon on it to call.
- **The call button is a split button** (M3 Expressive): the main part calls on the default
  account, the side opens the account picker. With one account, it is a plain round call button.
- A hardware keyboard types into the field directly; Enter calls.

### Incoming call

Full screen over the lock screen. The caller's avatar sits on a Material shape that slowly turns
and breathes while it rings (`MaterialShapes`, as Metadator's `ShapedIcon`). Name, address, the
account it came to (its color tints the screen). Two large buttons, *Decline* and *Answer*, a
*Decline with message* row later, and *Answer on speaker*. A setting replaces the buttons by a
swipe for those who answer with the phone in a pocket.

With a call already active: *Hold and answer*, *End and answer*, *Decline*.

### Ongoing call

```
┌──────────────────────────────┐
│  Lab Asterisk · 102     ▂▄▆  │  account (its color), quality
│                              │
│          (  avatar  )        │  shrinks as the call connects
│            Alice             │
│        sip:101@lab.local     │
│           03:42  🔒          │  timer, encryption when on
│                              │
│  [Mute] [Keypad] [Speaker ▾] │  ButtonGroup of toggle buttons,
│  [Hold] [ More… ]            │  contributed by CallActionProviders
│                              │
│        (   End call   )      │  wide, error container
└──────────────────────────────┘
```

- **Controls are toggle buttons in a button group**, their shape squaring when on, so muted reads
  from across the room. The audio route button names the route ("Speaker", "Pixel Buds") and opens
  the route sheet when more than two exist.
- **More** holds what does not fit, as a menu: call info, and later transfer, add call, record.
- **States are spoken and shown**: *Calling…*, *Ringing…*, *Connecting…*, *On hold*, *Held by
  Alice*, *Reconnecting…* (with the timer still running), each a live region for TalkBack.
- **When it ends**, the screen stays a moment with how it ended, in both texts, then closes.
- **Landscape and tabletop**: avatar and name on one side or half, controls on the other.

### Accounts

The list shows every account with its color, state and actions (enable, set default, diagnostics).
The editor groups fields as the add flow does, plus the per-account overrides: each overridable
option shows *Uses app default (Opus, G.722, PCMU)* with an *Override* action, and an overridden one
shows *Reset to default*. Changes that need a new registration say so, and saving re-registers and
shows the result like onboarding does.

### Settings

A root list of areas, each a `SettingsContributor`, with a search bar at the top that searches
every option's title, summary and keywords, and lands on the option highlighted. The catalogue is
below.

### Diagnostics

For whoever is setting up the Asterisk side, which in this course is everyone:

- **Overview**: engine state, each account's registration (with expiry countdown and last
  response), network (type, IPs, VPN), what transport and ports are in use.
- **SIP trace**: every message, as a list and as a ladder (one lane per party), filterable by
  Call-ID, method, status; tap for the raw message, copy or share.
- **Logs**: the engine's log, filtered by level and domain.
- **Network test**: resolve the server, reach its port on the account's transport, query the STUN
  server, and say in words what is wrong.
- **Share**: a bundle (logs, trace, configuration without passwords) through the share sheet.

## Components (`:core:designsystem`, `:calling:ui`, `:directory:ui`)

From Docucraft and Metadator, as they are: `FrostedLargeTopAppBar`, the settings items
(`SettingsItem`, `SettingSwitch`, `SettingSlider`, `SettingSegmentOption`, `PaletteStylePicker`),
`ConnectedChoices`, `TonalTextField`, `ActionMenu`/`PopupMenu`, `ToggleChip`, `ShapedIcon`, blur
halo and frosted surfaces.

New for Sona:

| Component | What it is |
|---|---|
| `SonaTextField` | The text field: filled and borderless, tonal at rest, the primary container and squarer corners when focused, the error container with the problem under it (read by TalkBack); a floating label, leading icon, trailing slot or clear button, supporting line. Every form uses it |
| `Dialpad`, `DialpadKey` | The keypad; each key morphs on press, sends DTMF tone and haptics through callbacks |
| `CallButton` | The round call button, morphing to a pill under the finger; `SplitCallButton` with the account picker |
| `EndCallButton` | Wide, error container, the largest target on the call screen |
| `CallControl` | A toggle button with icon and label for the call screen's button group |
| `Avatar` | Initials on one of Material's expressive shapes (cookie, clover, flower…) and a container color, both derived from the name, so the same person is always the same mark |
| `segmentedItems`, `segmentedListColors`, `ListHeadline` | Lists as segmented groups (`SegmentedListItem`): outer corners round, inner ones square, a gap between, corners morphing under the finger. Recents, Contacts, Settings and Diagnostics are made of them; each item's headline is a `ListHeadline` |
| `SectionHeader` | A group's heading, read as a heading by TalkBack; sticky in Recents and Contacts |
| `SegmentedChoiceList` | One choice among many as selectable segmented items with a radio mark: the options of a dialog (a setting with more than three values, the DTMF method) |
| `ConnectedChoice` | One choice among two to four, as a connected group of toggle buttons (Recents' All / Missed, a setting's values) |
| `ExpressiveSearch` | Material's search: a pill that grows into a full-screen view with the results (Contacts, Settings) |
| `SwipeToDeleteRow` | Swipe to delete, with a custom TalkBack action; Recents pairs it with a message to undo |
| `readableWidth()` | At most 840 dp wide and centered, so a tablet gets margins, not long lines |
| `RegistrationChip` | The account chip: color, name, animated state dot and word |
| `CallQualityBadge` | Three bars with Good / Fair / Poor, details on long press |
| `StatusText` | A failure's sentence with its technical line under it when *Show SIP details* is on |
| `OngoingCallBanner` | The banner over the app while a call exists |
| `LadderDiagram` | The SIP trace's lanes and arrows |

Every component takes a `Modifier`, reads tokens from `MaterialTheme`, has previews in light, dark,
large font and right-to-left, and has no knowledge of a feature.

## The expressive language

Material 3 Expressive, friendly but never toy-like. The keypad shows all of it; every screen follows.

- **Type is Google Sans Flex**, bundled (no font provider: nothing leaves the phone), one variable
  font set per role in `Type.kt`: display and headline fully round (`ROND` 100), titles and labels
  rounded (60), body text a hint of it (20), so the app is warm where it speaks up and plain where it
  explains. Each role's optical size is its own size. The emphasized roles
  (`titleMediumEmphasized`…) are a weight step heavier, for what the eye should land on first.
- **Text has four levels, and weight follows them.** What a thing is (a name, a setting) is
  `onSurface` and a list item's headline is `ListHeadline` (`bodyLargeEmphasized`); what explains
  it is body text, light, in `onSurfaceVariant` (a dialog's text, a list item's supporting line);
  what is said about it (a time, a state) is a label, which stays light; headings are primary. A
  hint, a subtitle or a description never wears a title's weight, and no text is faded with an
  alpha to step it back: it takes the role below. Only on a colored container, whose scheme has
  no variant role (an error card, a selected option), is the line below its content color at 75%.
- **Numbers have their own roles**: `SonaTheme.typography.numerals` (`large`, `medium`, `small`):
  round, a little wide, tabular figures. Monospace stays for what is read character by character
  (addresses, traces).
- **Shapes are pills at rest and square up under the finger**, on `MaterialTheme.motionScheme`'s
  spatial springs; fades and colors on its effects springs. An animated shape, color or scale is
  read in the draw phase (`graphicsLayer`, `drawBehind`), so a frame never recomposes.
- **Color comes in container pairs**: surface containers for most things, the secondary container
  for what is different in kind (`*`, `#`, side actions), primary for the one action that matters,
  the error container for failures. A disabled main action stays itself, faded, not grey.
- **Fields are `SonaTextField`**, never Material's outlined or filled fields: a tonal shape with no
  border, which takes the primary container and morphs when focused, as a key does when held. Each
  has a leading icon for what it holds; its label, while the field is empty, is a step quieter than
  what is typed. An example goes in the placeholder (shown only while typing).
- **Fields that belong together are one group** (`SonaTextFieldDefaults.segmentedShapes`): the
  segmented gap between them, the group's outer corners round and the inner ones small, as
  segmented lists are; the focused field rounds all its corners and stands out. In a group, an
  explanation of a default opens under its field while it is focused (`supportingOnFocus`), so the
  group stays one piece at rest; an error always shows.
- **Forms are grouped** under `SectionHeader`s, switches and options are segmented list items, a
  choice of two to four is a `ConnectedChoice`, what has a default waits behind a disclosure row
  that says what is inside, and the one action is a medium button at the foot (the account editor).
- **Lists are Material's expressive list items** (`SegmentedListItem`, or `ListItem` with `onClick`,
  `checked` or `selected`), never the `headlineContent` overload, which is deprecated. Settings: an
  area is a group per contributing feature under its `section`, each row with its icon; a large
  flexible app bar that collapses as the list scrolls; choices of up to three inline as a
  `ConnectedChoice`, more in a dialog as a `SegmentedChoiceList`.
- **One loud thing per screen**: the number, then the call button; everything else a size down.

## Motion and haptics

On the theme's expressive motion scheme: spatial springs for what moves or resizes, effects springs
for fades, navigation transitions from `NavigationMotion` only. Moments that get motion:

- Ringing: the avatar's shape turns and breathes; stops the instant the call is answered.
- Connecting to active: the avatar shrinks to its place, the timer slides in.
- Toggling a control: the button's shape changes on the spring.
- Registration changing: the chip's dot morphs between states.

Haptics: a tick per key (optional), a confirmation when a call connects, a double pulse when it
ends, the system's reject pattern when a call fails. Reduced motion (the system's animation scale
at 0) switches to the standard motion scheme and stops the ringing animation.

## Accessibility

- Dialpad keys read as "2, A B C"; long-press actions are custom actions.
- Call states and failures are live regions; the timer is not (it would talk every second).
- Answer and decline are reachable by TalkBack's actions on the screen itself, not only by gestures.
- Every screen works at 200% font scale; the call screen reflows rather than truncating names.
- Targets: at least 48 dp everywhere, 72 dp or more for call actions.
- Contrast follows the user's contrast setting and the theme's high contrast option.

## Language

Plain and specific: "102 is busy", not "Call failed: busy". The server is named by the account's
label, not its address, except in the technical line. Every string is a resource from the first
commit, and the plural and duration formats come from the platform.

## The options catalogue

What Sona lets the user change, by settings area. **MVP** marks what the first release ships; the
rest has its place in the architecture already (a `Setting` declaration and an item). **A** marks
an option an account can override.

### Accounts (per account)

| Option | Default | MVP |
|---|---|---|
| Label, color | server name, from the palette | ✓ |
| Display name, username (extension), password | — | ✓ |
| Authentication username, if different | the username | ✓ |
| Domain, registrar (host, port) | the server | ✓ |
| Transport: UDP, TCP, TLS | UDP | ✓ |
| Outbound proxy | none | ✓ |
| Registration expiry | 600 s | ✓ |
| Enabled, default account | first account | ✓ |
| Voicemail number | `*97` | ✓ |
| Dial plan: prefix to add, digits to strip, send `+` as | none | |
| Contact parameters, custom headers, user agent | none | |
| Ringtone (via its notification channel) | system | ✓ |

### Calls

| Option | Default | MVP |
|---|---|---|
| Account for calls: default, or ask every time | default | ✓ |
| Call waiting | on | ✓ |
| Do not disturb, and how to decline (busy `486` or declined `603`) | off, busy | ✓ |
| Auto-answer, after a delay, on speaker | off, 3 s | ✓ |
| Honor auto-answer headers from the server (paging, intercom) | off | |
| Incoming call: full screen or banner when unlocked | full screen | ✓ |
| Answer by: buttons or swipe | buttons | ✓ |
| Vibrate when ringing | system | ✓ |
| Proximity sensor during earpiece calls | on | ✓ |
| Confirm before calling from Recents | off | ✓ |
| Tap a recent to: call back or open details | details | ✓ |
| DTMF method: RFC 4733, SIP INFO, in-band **A** | RFC 4733 | ✓ |
| Play early media (ringback from the server) | on | |
| Maximum ring time before *No answer* | server's | |

### Audio

| Option | Default | MVP |
|---|---|---|
| Codecs: order and enabled (Opus, G.722, PCMU, PCMA, GSM, Speex) **A** | Opus, G.722, PCMU, PCMA | ✓ |
| Opus bitrate, Opus forward error correction | adaptive, on | |
| Echo cancellation, echo calibration | on | ✓ |
| Noise suppression, automatic gain | on | |
| Adaptive rate control | on | |
| Microphone and playback gain | 0 dB | |
| Jitter buffer size | adaptive | |
| Start calls on speaker | off | ✓ |
| Dialpad tones, dialpad haptics | on, on | ✓ |

### Network

| Option | Default | MVP |
|---|---|---|
| Always reachable (keep-alive service) | on with an enabled account | ✓ |
| Start on boot | on | ✓ |
| Use mobile data, or Wi-Fi only | both | ✓ |
| Local SIP port, random | random | |
| RTP port range | 10000–20000 | |
| IPv6 | on | |
| STUN server, ICE, TURN (server, credentials) **A** | off | |
| Keep-alive interval (UDP NAT, TCP) | 30 s | |
| QoS marking (DSCP) for SIP and RTP | off | |

### Security and privacy

| Option | Default | MVP |
|---|---|---|
| Media encryption: none, SRTP, ZRTP, DTLS-SRTP, and mandatory **A** | none | |
| Verify server certificates (TLS); import a CA for a lab's own certificate | on | |
| Hide caller on the lock screen | off | ✓ |
| Lock Sona with the device's credentials | off | |
| Block screenshots during calls | off | |

### Contacts and history

| Option | Default | MVP |
|---|---|---|
| Use device contacts | asks once | ✓ |
| Show only contacts with a SIP address or number | off | ✓ |
| Name order and sort order | system | ✓ |
| Group consecutive calls | on | ✓ |
| Keep history for (forever, a year, 90 days, 30 days) | forever | ✓ |
| Show Sona's calls in the phone app's history | on | ✓ |
| Clear history | — | ✓ |

### Appearance

| Option | Default | MVP |
|---|---|---|
| Theme: system, light, dark; high contrast; pure black | system | ✓ |
| Dynamic color, or a seed color and palette style | dynamic | ✓ |
| Blur: automatic, light, off (Android 12+; see [blur.md](blur.md)) | automatic | ✓ |
| Fonts per role (as Docucraft) | packaged default | |
| Dialpad letters | on | ✓ |
| Show addresses under names | on | ✓ |
| App language (per-app locale) | system | ✓ |

### Diagnostics and advanced

| Option | Default | MVP |
|---|---|---|
| Show SIP details in messages | off | ✓ |
| SIP trace capture | off | ✓ |
| Log level | warnings | ✓ |
| Call statistics on the call screen | off | |
| Export and import settings and accounts | — | |
| Reset everything | — | ✓ |

### Call readiness, About

The checklist, with each item's fix; version, engine version (Linphone's), licenses, source code.
