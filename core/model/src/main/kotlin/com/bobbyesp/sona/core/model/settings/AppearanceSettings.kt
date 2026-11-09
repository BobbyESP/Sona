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
