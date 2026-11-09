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

/** How calls behave. */
object CallSettings {
    /** A second call rings during one; off, it is declined as busy (486). */
    val CallWaiting = Setting.Toggle("calls.call_waiting", default = true)

    /** Stay registered while the app is closed, with a quiet notification, so calls can ring. */
    val AlwaysReachable = Setting.Toggle("calls.always_reachable", default = true)
}
