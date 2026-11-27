#!/usr/bin/env bash
set -euo pipefail
serial=$(adb get-serialno)
if [[ ! $serial =~ ^emulator-[0-9]+$ ]]; then
  echo 'Mobile UI checks need a disposable Android emulator.' >&2
  exit 1
fi
export ANDROID_SERIAL="$serial"
repository=$(git rev-parse --show-toplevel)
project="$repository/linkup-v0.2.0"
baseline=be9590f5f4c98b840552ed764e69a553b4acf11b
work=$(mktemp -d)
nav=com.android.internal.systemui.navbar
cutout=com.android.internal.display.cutout.emulation.tall
screens=/data/local/tmp/linkup-mobile
diagnostics="$project/android/app/build/mobile-diagnostics"
mkdir -p "$diagnostics"/{before,gesture,three-button}

active_nav=$(adb shell cmd overlay list | tr -d '\r' | sed -n "s/^\[x\] \($nav\..*\)$/\1/p")
old_ime=$(adb shell settings get secure show_ime_with_hard_keyboard | tr -d '\r')
cleanup() {
  adb shell cmd overlay disable "$cutout" >/dev/null || true
  if [[ -n $active_nav ]]; then
    adb shell cmd overlay enable-exclusive --category "$active_nav" >/dev/null || true
  fi
  if [[ $old_ime == null ]]; then
    adb shell settings delete secure show_ime_with_hard_keyboard >/dev/null || true
  else
    adb shell settings put secure show_ime_with_hard_keyboard "$old_ime" >/dev/null || true
  fi
  adb shell "rm -f $screens-*.png" >/dev/null || true
  rm -rf -- "$work"
}
trap cleanup EXIT
set_overlay() {
  adb shell cmd overlay enable-exclusive --category "$1"
  adb shell cmd overlay list | tr -d '\r' | grep -Fx "[x] $1"
}
collect_screens() {
  local set=$1
  shift
  for name in "$@"; do
    adb pull "$screens-$name.png" "$diagnostics/$set/$name.png"
  done
}
# Use the runner's compact physical display. wm overrides lose the emulated cutout.
adb shell wm size
adb shell wm density
adb shell settings put secure show_ime_with_hard_keyboard 1
set_overlay "$nav.gestural"
set_overlay "$cutout"

mkdir "$work/old"
git -C "$repository" archive "$baseline" linkup-v0.2.0/android linkup-v0.2.0/ui \
  linkup-v0.2.0/version.properties | tar -x -C "$work/old"
sources=app/src/androidTest/java/io/linkup/app
legacy="$work/old/linkup-v0.2.0/android"
cp "$project/android/$sources/MobileScreenshotTest.java" "$legacy/$sources/"
(
  cd "$legacy"
  gradle :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=io.linkup.app.MobileScreenshotTest
)
collect_screens before portrait-settings landscape-settings
adb uninstall io.linkup.app.test >/dev/null 2>&1 || true
adb uninstall io.linkup.app >/dev/null 2>&1 || true
adb shell "rm -f $screens-*.png"

cd "$project/android"
result=0
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.requireCutout=true || result=$?
cp -R app/build/outputs/androidTest-results/connected/debug "$diagnostics/gesture/results" || true
if [[ $result -ne 0 ]]; then
  collect_screens gesture portrait-settings landscape-settings portrait-keyboard landscape-keyboard || true
  exit "$result"
fi
collect_screens gesture portrait-settings landscape-settings portrait-keyboard landscape-keyboard
set_overlay "$nav.threebutton"
adb shell "rm -f $screens-*.png"
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.requireCutout=true \
  '-Pandroid.testInstrumentationRunnerArguments.class=io.linkup.app.MobileUiTest#keepsFocusedInputsAndActionsReachableInBothOrientations' || result=$?
cp -R app/build/outputs/androidTest-results/connected/debug "$diagnostics/three-button/results" || true
if [[ $result -ne 0 ]]; then
  collect_screens three-button portrait-keyboard landscape-keyboard || true
  exit "$result"
fi
collect_screens three-button portrait-keyboard landscape-keyboard
