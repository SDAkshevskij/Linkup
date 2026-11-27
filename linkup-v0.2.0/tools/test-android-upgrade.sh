#!/usr/bin/env bash
set -euo pipefail
serial=$(adb get-serialno)
if [[ ! $serial =~ ^emulator-[0-9]+$ ]]; then
  echo 'The upgrade test needs a disposable Android emulator.' >&2
  exit 1
fi
export ANDROID_SERIAL="$serial"
root=$(git rev-parse --show-toplevel)
project="$root/linkup-v0.2.0"
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
signer="${ANDROID_HOME:?}/build-tools/35.0.0/apksigner"
mkdir "$work/old"
git -C "$root" archive 92bec824af9e56bb48d6384029812fdb0e039b63 \
  linkup-v0.2.0/android linkup-v0.2.0/ui | tar -x -C "$work/old"
legacy="$work/old/linkup-v0.2.0/android"
test_sources=app/src/androidTest/java/io/linkup/app
# Build each test APK against its target so target-provided libraries match that version.
cp "$project/android/$test_sources/"{SmokeTest,UpgradeTest}.java "$legacy/$test_sources/"
(
  cd "$legacy"
  gradle :app:assembleDebug :app:assembleDebugAndroidTest
)
(
  cd "$project/android"
  gradle :app:assembleRelease :app:assembleDebugAndroidTest
)
for key in test wrong; do
  keytool -genkeypair -keystore "$work/$key.p12" -storetype PKCS12 -storepass disposable-ci-only \
    -alias linkup -keyalg RSA -keysize 2048 -validity 2 -dname "CN=LinkUp CI $key"
done
sign() {
  "$signer" sign --ks "$work/$1.p12" --ks-type PKCS12 --ks-key-alias linkup \
    --ks-pass pass:disposable-ci-only --out "$2" "$3"
  "$signer" verify "$2"
}
old="$legacy/app/build/outputs/apk/debug/app-debug.apk"
new="$project/android/app/build/outputs/apk/release/app-release-unsigned.apk"
tests=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
sign test "$work/old.apk" "$old"
sign test "$work/new.apk" "$new"
sign wrong "$work/wrong.apk" "$new"
sign test "$work/old-tests.apk" "$legacy/$tests"
sign test "$work/new-tests.apk" "$project/android/$tests"
adb uninstall io.linkup.app.test >/dev/null 2>&1 || true
adb uninstall io.linkup.app >/dev/null 2>&1 || true
adb install "$work/old.apk"
adb install -t "$work/old-tests.apk"
instrument() {
  adb shell am instrument -w -e class io.linkup.app.UpgradeTest -e upgradePhase "$1" \
    io.linkup.app.test/androidx.test.runner.AndroidJUnitRunner | tee "$work/$1.log"
  tr -d '\r' < "$work/$1.log" | grep -Fx 'OK (1 test)'
}
instrument seed
if result=$(adb install -r "$work/wrong.apk" 2>&1); then
  echo 'Android accepted an incompatible signature' >&2
  exit 1
fi
printf '%s\n' "$result"
[[ $result == *INSTALL_FAILED_UPDATE_INCOMPATIBLE* ]]
adb install -r "$work/new.apk"
adb install -r -t "$work/new-tests.apk"
instrument verify
