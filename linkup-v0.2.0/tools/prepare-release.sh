#!/usr/bin/env bash
set -euo pipefail
: "${LINKUP_KEYSTORE_BASE64:?Set LINKUP_KEYSTORE_BASE64 in linkup-release}"
: "${LINKUP_KEYSTORE_PASSWORD:?Set LINKUP_KEYSTORE_PASSWORD in linkup-release}"
: "${RUNNER_TEMP:?}" "${ANDROID_HOME:?}"
umask 077
project=$(cd "$(dirname "$0")/.." && pwd)
code=$(sed -n 's/^versionCode=//p' "$project/version.properties")
name=$(sed -n 's/^versionName=//p' "$project/version.properties")
[[ $code =~ ^[1-9][0-9]{0,8}$ && $name =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
output="$RUNNER_TEMP/linkup-release"
mkdir "$output"
private=$(mktemp -d "$RUNNER_TEMP/linkup-signing.XXXXXX")
trap 'rm -rf -- "$private"' EXIT
printf '%s' "$LINKUP_KEYSTORE_BASE64" | base64 --decode > "$private/linkup.p12"
unset LINKUP_KEYSTORE_BASE64
tools="$ANDROID_HOME/build-tools/35.0.0"
apk="$output/LinkUp-Android-$code.apk"
keytool -exportcert -storetype PKCS12 -keystore "$private/linkup.p12" -alias linkup \
  -storepass:env LINKUP_KEYSTORE_PASSWORD -file "$private/signer.der"
fingerprint=$(sha256sum "$private/signer.der" | cut -d ' ' -f1)
"$tools/apksigner" sign --ks "$private/linkup.p12" --ks-type PKCS12 --ks-key-alias linkup \
  --ks-pass env:LINKUP_KEYSTORE_PASSWORD --out "$apk" "$project/android/app/build/outputs/apk/release/app-release-unsigned.apk"
unset LINKUP_KEYSTORE_PASSWORD
cert() {
  "$tools/apksigner" verify --print-certs "$1" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p'
}
[[ $(cert "$apk") == "$fingerprint" ]]
badging=$("$tools/aapt" dump badging "$apk")
[[ $badging == "package: name='io.linkup.app' versionCode='$code' versionName='$name'"* ]]

python3 "$project/tools/package_linux.py" "$output/LinkUp-Linux-$code.zip"
cd "$output"
sha256sum "LinkUp-Android-$code.apk" "LinkUp-Linux-$code.zip" > SHA256SUMS
sha256sum -c SHA256SUMS
