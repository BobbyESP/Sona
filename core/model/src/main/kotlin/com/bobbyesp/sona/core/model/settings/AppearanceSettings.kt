/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.model.settings

/** Light, dark, or whatever the system is. */
enum class ThemeMode {
    System,
    Light,
    Dark,
}

/** How a seed color becomes a scheme (MaterialKolor's styles). */
enum class PaletteStyle {
    TonalSpot,
    Vibrant,
    Expressive,
    FruitSalad,
    Rainbow,
    Monochrome,
}

/**
 * How much the app blurs. The phone may blur less on its own (in battery saver, when it is hot, or
 * below Android 12, where it never blurs); a choice can lower that, never raise it.
 */
enum class BlurPreference {
    /** As much as the phone can afford: frosted bars, and the screen behind a sheet. */
    Automatic,

    /** Frosted bars only, at a lower resolution: lighter on the battery and on an older phone. */
    Light,

    /** No blur: bars and sheets are solid. */
    Off,
}

/** Sona's seed: a deep teal, until the user picks another. */
const val SONA_SEED_COLOR: Int = 0xFF00696B.toInt()

/** How the app looks. */
object AppearanceSettings {
    val Theme = Setting.choice("appearance.theme", ThemeMode.System)

    /** Colors from the wallpaper (Android 12+); otherwise from [SeedColor]. */
    val DynamicColor = Setting.Toggle("appearance.dynamic_color", default = true)

    /** An ARGB color. */
    val SeedColor = Setting.Number("appearance.seed_color", default = SONA_SEED_COLOR)

    val Palette = Setting.choice("appearance.palette_style", PaletteStyle.TonalSpot)

    /** Pure black surfaces in the dark theme. */
    val PureBlack = Setting.Toggle("appearance.pure_black", default = false)

    /** Shown from Android 12, where blur exists. */
    val Blur = Setting.choice("appearance.blur", BlurPreference.Automatic)
}

/** What the app remembers about itself. */
object AppSettings {
    /** Whether the first start's welcome was seen, or an account exists. */
    val OnboardingDone = Setting.Toggle("app.onboarding_done", default = false)
}

/** What an incoming call is told when it is refused without ringing. */
enum class DeclineMode {
    /** `486 Busy Here`: the caller hears it is busy. */
    Busy,

    /** `603 Decline`: the caller is told the call was refused. */
    Declined,
}

/** How calls behave. */
object CallSettings {
    /** A second call rings during one; off, it is declined as busy (486). */
    val CallWaiting = Setting.Toggle("calls.call_waiting", default = true)

    /** Stay registered while the app is closed, with a quiet notification, so calls can ring. */
    val AlwaysReachable = Setting.Toggle("calls.always_reachable", default = true)

    /** Incoming calls do not ring: they are refused at once, and the caller is told how. */
    val DoNotDisturb = Setting.Toggle("calls.do_not_disturb", default = false)

    val DeclineWith = Setting.choice("calls.decline_with", DeclineMode.Busy)

    /** How a key pressed during a call reaches the other side. */
    val Dtmf = Setting.choice("calls.dtmf_method", DtmfMethod.Rfc4733, overridable = true)
}

/** How DTMF digits are sent. A server says which it listens to (Asterisk: `dtmf_mode`). */
enum class DtmfMethod {
    /** As RTP events (RFC 4733, once RFC 2833): what nearly every server expects. */
    Rfc4733,

    /** As SIP `INFO` requests, beside the audio. */
    SipInfo,

    /** As tones in the audio itself; lost by codecs that compress hard. */
    InBand,
}

/** An audio codec Sona can offer, with the name SDP gives it. */
enum class AudioCodec(val mime: String, val label: String) {
    Opus("opus", "Opus"),
    G722("G722", "G.722"),
    Pcmu("PCMU", "PCMU (G.711 µ-law)"),
    Pcma("PCMA", "PCMA (G.711 A-law)"),
    Gsm("GSM", "GSM"),
    Speex("speex", "Speex");

    companion object {
        /** The name to show for a codec as [AudioSettings.Codecs] ranks it (by its enum name). */
        fun labelOf(id: String): String = entries.firstOrNull { it.name == id }?.label ?: id
    }
}

/** How calls sound. */
object AudioSettings {
    /** The codecs offered, first choice first. The two sides settle on the first both have. */
    val Codecs =
        Setting.Ranked(
            "audio.codecs",
            default =
                Ranking(
                    AudioCodec.entries.map { codec ->
                        Ranking.Entry(
                            codec.name,
                            enabled = codec != AudioCodec.Gsm && codec != AudioCodec.Speex,
                        )
                    },
                ),
            overridable = true,
        )

    /**
     * Remove from the microphone what the speaker plays, so the other side does not hear itself.
     */
    val EchoCancellation = Setting.Toggle("audio.echo_cancellation", default = true)

    /** The keypad sounds a tone under the finger, in the dialer and during a call. */
    val DialpadTones = Setting.Toggle("audio.dialpad_tones", default = true)
}

/** Which networks Sona uses. */
object NetworkSettings {
    /** Register and call over mobile data too; off, only over Wi-Fi. */
    val MobileData = Setting.Toggle("network.mobile_data", default = true)
}

/** How much the SIP engine writes to the log. */
enum class LogLevel {
    Errors,
    Warnings,

    /** Every SIP message too. */
    Messages,
    Debug,
}

/** What helps to find a problem. */
object DiagnosticsSettings {
    val Logging = Setting.choice("diagnostics.log_level", LogLevel.Warnings)

    /** Keep the SIP messages Sona sends and receives, to read them in the diagnostics. */
    val TraceCapture = Setting.Toggle("diagnostics.trace_capture", default = false)
}

/** How long the call history is kept. */
enum class HistoryRetention(val days: Int?) {
    Forever(null),
    Year(365),
    Days90(90),
    Days30(30),
}

/** What tapping an entry in Recents does. */
enum class RecentTapAction {
    /** Opens its details, from where it can be called. */
    Details,

    /** Calls the person back at once. */
    CallBack,
}

/** The call history and how Recents behaves. */
object HistorySettings {
    /** Calls in a row with the same person are one entry. */
    val GroupCalls = Setting.Toggle("history.group_calls", default = true)

    val Retention = Setting.choice("history.retention", HistoryRetention.Forever)

    val TapAction = Setting.choice("history.tap_action", RecentTapAction.Details)

    /** Ask before calling back from Recents. */
    val ConfirmCallBack = Setting.Toggle("history.confirm_call_back", default = false)
}

/** The people Sona knows. */
object ContactsSettings {
    /** Show the phone's contacts next to Sona's own (they are read, never changed). */
    val UseDeviceContacts = Setting.Toggle("contacts.use_device", default = true)
}
