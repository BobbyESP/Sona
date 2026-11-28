#!/usr/bin/env bash
#
# Copyright (C) 2026  Gabriel Fontán (BobbyESP)
#
# Regenerates assets/screenshots/<variant>/: the demo app, driven on a phone by
# app/src/androidTestDemo/.../ScreenshotTour.kt, once per variant (language and theme).
#
#   scripts/screenshots.sh              every variant
#   scripts/screenshots.sh es-dark      only the ones named
#   ANDROID_SERIAL=<serial> scripts/screenshots.sh   one of several devices
#
# The phone must be unlocked and awake. Its theme, the status bar and the demo app's language are
# changed for the run and put back at the end, even when it fails. See docs/releasing.md.

set -euo pipefail

# Git Bash would otherwise turn /sdcard/... arguments into Windows paths.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

cd "$(dirname "$0")/.."

PACKAGE=com.bobbyesp.sona.demo.debug
RUNNER=$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner
TOUR=com.bobbyesp.sona.screenshots.ScreenshotTour
DEVICE_DIR=/sdcard/Android/data/$PACKAGE/files/screenshots

# name:locale:night
ALL_VARIANTS=("es-light:es-ES:no" "es-dark:es-ES:yes")

die() {
  echo "screenshots: $*" >&2
  exit 1
}

sh_() { adb shell "$@" | tr -d '\r'; }

demo() {
  adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null
}

locked() {
  local state
  state=$(sh_ dumpsys window)
  [[ $state == *isKeyguardShowing=true* || $state == *mDreamingLockscreen=true* ]]
}

command -v adb > /dev/null || die "adb is not on the PATH"
[ "$(adb get-state 2> /dev/null)" = device ] ||
  die "no device: connect one (or set ANDROID_SERIAL when there are several)"

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
sleep 1
if locked; then
  die "the phone is locked: unlock it, and keep it awake (screen timeout, or Stay awake) for the run"
fi

ORIGINAL_NIGHT=$(sh_ cmd uimode night | sed 's/.*: *//')
ORIGINAL_LOCALES=$(sh_ cmd locale get-app-locales "$PACKAGE" | sed -n 's/.*\[\(.*\)\].*/\1/p')
ORIGINAL_DEMO_ALLOWED=$(sh_ settings get global sysui_demo_allowed)

restore() {
  set +e
  echo "screenshots: restoring the device"
  demo exit
  if [ "$ORIGINAL_DEMO_ALLOWED" = null ]; then
    adb shell settings delete global sysui_demo_allowed > /dev/null
  else
    adb shell settings put global sysui_demo_allowed "$ORIGINAL_DEMO_ALLOWED"
  fi
  adb shell cmd uimode night "${ORIGINAL_NIGHT:-auto}" > /dev/null
  adb shell cmd locale set-app-locales "$PACKAGE" --locales "$ORIGINAL_LOCALES" > /dev/null
  adb shell am force-stop "$PACKAGE"
}
trap restore EXIT

echo "screenshots: building and installing the demo app and its tour"
./gradlew :app:installDemoDebug :app:installDemoDebugAndroidTest

adb shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 1200
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile show -e datatype none -e level 4 -e fully true
demo notifications -e visible false

run_variant() {
  local name=$1 locale=$2 night=$3
  local pulled="build/screenshots/$name" target="assets/screenshots/$name"
  echo "screenshots: $name ($locale, night $night)"

  # The data first (it empties the app's external files too), then the language: clearing the
  # data of an app must not be able to undo what the next lines set.
  adb shell am force-stop "$PACKAGE"
  adb shell pm clear "$PACKAGE" > /dev/null
  adb shell cmd locale set-app-locales "$PACKAGE" --locales "$locale" > /dev/null
  adb shell cmd uimode night "$night" > /dev/null
  sleep 2

  local output
  output=$(adb shell am instrument -w -e class "$TOUR" "$RUNNER" | tr -d '\r')
  echo "$output" | tail -n 25

  rm -rf "$pulled"
  mkdir -p "$pulled"
  adb pull "$DEVICE_DIR/." "$pulled" > /dev/null ||
    die "$name: no pictures on the device in $DEVICE_DIR"

  if ! grep -q '^OK (1 test)' <<< "$output"; then
    die "$name: the tour failed; what was saved is in $pulled (failure.png is the screen it stopped at)"
  fi

  rm -rf "$target"
  mkdir -p "$target"
  cp "$pulled"/*.png "$target"/
}

selected=("$@")
for variant in "${ALL_VARIANTS[@]}"; do
  IFS=: read -r name locale night <<< "$variant"
  if [ ${#selected[@]} -eq 0 ] || [[ " ${selected[*]:-} " == *" $name "* ]]; then
    run_variant "$name" "$locale" "$night"
  fi
done

echo "screenshots: done, in assets/screenshots/"
