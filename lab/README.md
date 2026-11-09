# The Asterisk lab

The Asterisk Sona is tested against: three extensions, and a number for each case of the call
matrix in [docs/roadmap.md](../docs/roadmap.md#the-call-matrix).

| File | What it holds |
|---|---|
| [`asterisk/pjsip.conf`](asterisk/pjsip.conf) | Transports (UDP and TCP on 5060) and extensions 101, 102, 103 |
| [`asterisk/extensions.conf`](asterisk/extensions.conf) | The dial plan: extensions, echo test (600), always busy (486), voicemail (`*97`) |
| [`asterisk/voicemail.conf`](asterisk/voicemail.conf) | A voicemail box per extension |

## Setting it up

1. Install Asterisk 20 or later with the PJSIP channel driver (the default).
2. Copy the three files to `/etc/asterisk/`, keeping a copy of the originals.
3. **Change every password** in `pjsip.conf` (`change-me-…`) and every PIN in `voicemail.conf`.
4. In the Asterisk console (`asterisk -rvvv`): `core reload`, then `pjsip show endpoints`.

Each extension appears as *Unavailable* until a phone registers.

## Adding it to Sona

From phase 1, in *Add account › Asterisk extension*: the server's address, the extension (`102`)
and its password. Until then the `demo` flavor shows a fake *Lab Asterisk · 102*.

## Checking from the server

| Command | Shows |
|---|---|
| `pjsip show contacts` | Which phones are registered, from which address |
| `pjsip set logger on` | Every SIP message, as Sona's trace will show it |
| `rtp set debug on` | Whether audio flows, and to where |
| `core show channels` | Calls in progress |

These files have not yet been run against a live Asterisk; the first registration in phase 1 is
their test.
