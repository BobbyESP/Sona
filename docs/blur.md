# Blur

How the app blurs, and how to take the same blur to another app. Everything is in one package,
`core/designsystem/src/main/kotlin/com/bobbyesp/sona/core/designsystem/blur/`, with its test in
`core/designsystem/src/test/kotlin/.../blur/`. The package depends on Compose, Material 3 and Haze
only: nothing in it names Sona.

## What it does

- **Four calls.** `rememberBlurSource()` and `Modifier.blurSource(source)` on the content that
  scrolls; `Modifier.frosted(source, containerColor, shape, amount = { … })` on the surface over
  it; `Modifier.outOfFocus { radius }` on content itself (the screen behind a sheet);
  `Modifier.blurHalo(source, shape, strength = { … })` around a floating element, where a shadow
  would darken. Callers never see a Haze type.
- **A level, for the whole app.** `BlurLevel` is `Off`, `Reduced` or `Full`. The theme provides it
  once with `ProvideBlurLevel`, and every modifier falls back on its own:

  | Level | When | What blurs |
  |---|---|---|
  | `Off` | below Android 12, Android Go, battery saver, a hot phone (thermal *severe*), or the user's choice | nothing: a frosted surface is its container color at 96 %, a halo and out-of-focus draw nothing |
  | `Reduced` | under 3 GB of memory, a warm phone (thermal *moderate*), the system's window blur off, or the user's choice | frosted surfaces only, at Haze's lowest input resolution |
  | `Full` | otherwise | everything, at Haze's balanced resolution |

  `rememberDeviceBlurLevel()` listens for battery saver, the thermal status and the system's window
  blur setting while the theme is shown, so the level changes as the phone does.
  `rememberBlurLevel(preferred)` caps it with the user's choice: less blur than the device allows,
  never more.
- **Smooth by construction.** What animates is a lambda read while drawing, so an animation
  redraws and never recomposes. Haze builds its blur again for a new color or radius, and only
  fades it for a new alpha, so a frost animates its `amount`, never its color, and a halo's radius
  moves in whole dps (a dozen rebuilds per fade, not one per frame). A source blurred by two
  surfaces renders once for both.

## Taking it to another app

1. **Dependencies**, in the design system module:

   ```toml
   haze = "2.1.0"
   haze = { group = "dev.chrisbanes.haze", name = "haze", version.ref = "haze" }
   haze-blur = { group = "dev.chrisbanes.haze", name = "haze-blur", version.ref = "haze" }
   ```

   `Modifier.outOfFocus` uses Compose's `Modifier.blur { blurRadiusSpec = … }` (compose-bom-alpha
   2026.11). With an older Compose, write its last line as
   `graphicsLayer { renderEffect = BlurEffect(r, r) }` with `r = radius().toPx()`.
2. **Copy the package and its test**, and change their `package` line. Nothing else in them names
   the app.
3. **Provide the level in the theme**, around `MaterialTheme`:

   ```kotlin
   ProvideBlurLevel(rememberBlurLevel(preferred = config.blur)) {
       MaterialExpressiveTheme(/* … */, content = content)
   }
   ```
4. **Replace the old calls.** A `HazeState` becomes `rememberBlurSource()`, `hazeSource` becomes
   `blurSource`, `hazeBlur`/`frosted(state, style)` becomes `frosted(source, containerColor)`, and
   `Modifier.blur` behind an overlay becomes `outOfFocus { radius }`. A surface that used to animate
   its frost's color draws its resting color behind (`drawBehind { drawRect(surface) }`) and
   animates `amount` instead: see `FrostedLargeTopAppBar` in `ScreenChrome.kt`.
5. **The setting** (optional): a choice of *Automatic*, *Light* or *Off* stored by the app, mapped to
   `BlurLevel.Full`, `Reduced` and `Off`, and passed as `preferred`. Hide it below Android 12, where
   there is nothing to choose. In Sona: `AppearanceSettings.Blur`, `SonaThemeConfig.blur` and the
   item in `feature/settings/AppearanceSettings.kt`.
