# Messaging

Text, pictures and files between extensions, over SIP `MESSAGE` (RFC 3428) through the user's own
server. Nothing else carries them: no file server, no push, no cloud. What this costs, and how
Sona makes it work anyway, follows from what Asterisk does with a `MESSAGE`.

## What Asterisk allows (read from `res_pjsip_messaging.c`, Asterisk 20)

| Fact | Consequence for Sona |
|---|---|
| Out of a dialog, only `text/plain` is accepted; anything else is `415 Unsupported Media Type` | Every message Sona sends is `text/plain`. No CPIM, no IMDN, no `is-composing` XML, no multipart |
| The body is copied into a 1024-byte buffer (`MAX_BODY_SIZE`); a longer one does not fit and reaches the dialplan empty | No body is ever longer than the account's **message size limit** (default 700 bytes) |
| PJSIP sends a request over 1300 bytes by TCP instead of UDP, which an endpoint registered over UDP may not answer | The default limit keeps the whole request under 1300 bytes |
| The server answers `202 Accepted` as soon as the dialplan has the message, before delivering it | `202` means *sent* (the server took it), not *delivered*. Delivery is only known when the other side says so |
| Headers are not passed on unless the dialplan copies them | Sona's own signalling travels in the body, not in `X-` headers |
| A recipient that is not registered never gets the message, and the sender is not told | A message to Sona that does not arrive stays *Sent* and is never shown as delivered |

Other servers (FreeSWITCH, Kamailio, a direct peer) take larger bodies; the limit is a setting
(per account, *Messages › Largest message the server relays*: 700 B, 1 KB, 4 KB, 16 KB, 60 KB).

## Two kinds of peer

Sona speaks plain SIP text to everyone, and a little more to other Sona phones:

- **Plain peers** (any SIP phone, Asterisk's dialplan, or a peer not yet known to be Sona) get the
  text as typed. A text longer than the limit is sent in parts, split at word boundaries and never
  inside a UTF-8 sequence or a surrogate pair. Status: *Sending*, *Sent* (`200`/`202`), *Failed*.
- **Sona peers** are known because they sent a Sona envelope (below). With them, texts carry an id,
  and there are delivery and read receipts, typing indicators, long texts reassembled, and files.

A Sona phone never sends an envelope to a peer that is not known to be Sona, except the file
offer, which is written to be read by a person. So two Sona phones that only ever exchanged plain
texts do not know of each other yet: the first file offered (or any envelope, from a peer that
already knows) makes them Sona peers, and each answers the first envelope it hears with `hi`.
Until then their texts are plain, with *Sent* as the last status.

## Envelopes

A Sona envelope is a `text/plain` body whose **last line** starts `sona:1:`. Everything is ASCII
in the envelope line; ids are 10 characters of base32.

| Line | Meaning |
|---|---|
| `sona:1:t:<id>:<n>/<of>:<text>` | Part `n` of `of` of a text with an id (a short text is `1/1`) |
| `sona:1:offer:<base64url JSON>` | A file offered: `{id, name, mime, size, sha256, chunk, chunks, w?, h?}`. The lines above it say, for a person, what it is: "📎 holiday.jpg · 184 KB — sent with Sona" |
| `sona:1:accept:<id>` / `sona:1:decline:<id>` | The receiver takes or refuses the file |
| `sona:1:c:<id>:<index>:<base64>` | One chunk of a file |
| `sona:1:miss:<id>:<ranges>` | Chunks still missing (`0-12,40,77-80`), asked for again |
| `sona:1:ack:<id>:<received>` | Progress, at most every 2 seconds |
| `sona:1:done:<id>` / `sona:1:fail:<id>:<reason>` | The file arrived whole (its SHA-256 matched), or could not |
| `sona:1:cancel:<id>` | Either side stops a transfer |
| `sona:1:rcpt:d:<ids>` / `sona:1:rcpt:r:<ids>` | Delivered to / read on the other phone (comma-separated ids) |
| `sona:1:typing` | Typing now; shown for 6 seconds, sent at most every 4 |
| `sona:1:hi` | "I am Sona", sent once in answer to the first envelope from a peer not yet known |

A body that does not end in a valid envelope line is a plain text, shown as it came.

Delivered receipts go for every text with an id, gathered for 400 ms; read receipts and typing only
when *Send read receipts* and *Send typing indicators* are on, and only ever to Sona peers.

## Files

1. **Prepare.** A picture is resized (longest side 2048 px) and re-encoded unless *Send pictures in
   original quality* is on; its size and SHA-256 are worked out. Files up to the limit set in
   *Messages* (10 MB by default) can be sent; a larger one is refused with the reason.
2. **Offer.** One message, readable by anyone: name, size, and the envelope. The sender shows
   *Waiting for 102 to download*.
3. **Accept.** The receiving Sona accepts by itself up to *Download automatically up to* (2 MB by
   default); a larger file waits for a tap on *Download*. A plain phone just shows the offer's
   text, and nothing else is ever sent to it.
4. **Stream.** Chunks are sent with a window of eight in flight: the next goes when the server has
   answered one. A chunk the server refuses or does not answer (`408`, `503`, no network) is sent
   again with back-off (2 s, 4 s, 8 s… up to 30 s, seven times). Chunk size follows from the message
   size limit: `(limit − 28) / 4 × 3` bytes, 504 at 700 bytes. How far the sender got is saved
   every 64 chunks or every second, so a restart goes on from there.
5. **Repair.** The receiver writes each chunk at its place in a file of its own, keeps which ones
   it has in the database (so a transfer survives the app being closed), and when chunks stop
   coming for 8 seconds asks for the missing ones (`miss`). After five unanswered requests the
   transfer fails, with *Retry*.
6. **Finish.** The receiver checks the SHA-256 (streamed from the file), answers `done`, and the
   file is in the conversation; the sender shows it as delivered. A mismatch discards what came
   and answers `fail:…:hash`; *Retry* on the sender offers the file again from the start.

Either side can `cancel`; deleting a message in transfer cancels it (or declines an offer not yet
taken). A chunk of a transfer nobody knows of (its conversation was deleted) is answered `cancel`.

What it costs: every message is a SIP transaction to the server and one from it, and base64 adds a
third. About 2 000 messages carry a megabyte; on a local network that is seconds, over the
internet tens of seconds. This is why pictures are compressed by default and files have a limit.

## Storage

The app's Room database is the only record (schema 4, an automatic migration from 3):
`conversations` (one per account and peer, with what is known of the peer), `messages`,
`attachments` (a message's file and its transfer), `transfer_chunks` (which chunks of an incoming
file arrived, one bit each, in a table of its own so listing a conversation never reads it) and
`message_parts` (the parts of a long incoming text, until every part came).

Linphone stores nothing the app depends on: an outgoing message is created not to be stored, and
an incoming one is deleted from Linphone's history as soon as it has been handed over (what is left
is an empty basic chat room per peer, which nothing reads). Linphone's IMDN and is-composing are
off; Sona's receipts and typing travel in bodies.

Files are in the app's own storage, `files/messages/`: `sent/` (the copy made to send, so a send
survives the picker's grant and can be retried), `received/` (each file that arrived whole, in a
folder of its own) and `incoming/` (one file per transfer coming in, written in place). They are
opened through `MessageFileProvider`, authority `${applicationId}.messages`, which serves `sent/`
and `received/` only; `Messenger.uriOf(attachment)` gives the `content://` address, to open with
`FLAG_GRANT_READ_URI_PERMISSION`. They are saved elsewhere only when the user picks a place.

## For the screens

Everything goes through `Messenger` (`:messaging:domain`, a single in the app, started by
`EngineBootstrap` before the engine):

| What | How |
|---|---|
| The list | `conversations: Flow<List<ConversationSummary>>` (name from the directory, last message, unread count); `conversation(id)` |
| A conversation | `messages(id): Flow<List<Message>>` (oldest first), `typing(id): Flow<Boolean>` |
| The tab's badge | `totalUnread: Flow<Int>` |
| Notifications | `events: SharedFlow<MessengerEvent>`: `Received(conversation, message, visible)` |
| Opening one | `open(account, peer): ConversationId?`, then `setVisibleConversation(id)` while it is on screen (and `null` when it leaves); what arrives in it is read at once |
| Writing | `sendText(id, text)`, `sendFile(id, contentUri)` → `SendOutcome` (`Queued`, `Empty`, `NoConversation`, `TooLong`, `FileTooLarge`, `FileUnreadable`); `notifyTyping(id)` on every change of the field |
| Acting on one | `retry`, `download`, `decline`, `cancel`, `delete(ids)`, `deleteConversation`, `markRead` |
| A file | `uriOf(attachment)` |

A `Message` has a `MessageStatus` (`Sending`, `Sent`, `Delivered`, `Read`, `Failed`, `Received`),
an optional `Attachment` with its `TransferState` and `progress`, and, when something went wrong, a
`MessageFailure` (`reason: FailureReason`, `status: SipStatus?`, `technical` for *Show SIP
details*).

## Failures, in words and in SIP

| SIP | What the user reads |
|---|---|
| `404`, `484`, `604` | There is no extension 205 on *Lab Asterisk* |
| `415` | The server does not relay this kind of message |
| `401`, `403`, `407` | The server refused the message. Check this account's username and password |
| `413`, `513` | The server takes shorter messages: lower *Largest message the server relays* |
| `408`, no answer | *Lab Asterisk* did not answer |
| `480` | 102 is not online right now |
| `503`, transport error | *Lab Asterisk* (192.168.1.10:5060, UDP) did not answer |
| account not registered | *Lab Asterisk* is not connected, so nothing was sent |
| anything else | Not sent (`500 Server Internal Error`) |

A transfer has its own: the receiver stopped answering (`TimedOut`), the file did not match its
hash (`Corrupted`), the other phone could not take it (`PeerFailed`), this phone could not write it
(`Storage`), the file to send is gone (`FileMissing`). `408`, `500`, `503`, `504` and no answer are
tried again with back-off; any other answer is final. An account that is not registered fails a
text at once, and a transfer waits for it.

## Asterisk

Messages need the endpoint's `message_context` and a dialplan that sends them on. The `lab/`
configuration has both:

```
; pjsip.conf, on each endpoint
message_context=messages

; extensions.conf
[messages]
exten => _X.,1,MessageSend(pjsip:${EXTEN},${MESSAGE(from)})
 same => n,Hangup()
```
