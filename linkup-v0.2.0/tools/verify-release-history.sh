#!/usr/bin/env bash
set -euo pipefail
: "${RUNNER_TEMP:?}" "${GITHUB_REPOSITORY:?}" "${ANDROID_HOME:?}"
project=$(cd "$(dirname "$0")/.." && pwd)
code=$(sed -n 's/^versionCode=//p' "$project/version.properties")
tools="$ANDROID_HOME/build-tools/35.0.0"
private=$(mktemp -d "$RUNNER_TEMP/linkup-history.XXXXXX")
trap 'rm -rf -- "$private"' EXIT
cert() {
  "$tools/apksigner" verify --print-certs "$1" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p'
}
fingerprint=$(cert "$RUNNER_TEMP/linkup-release/LinkUp-Android-$code.apk")
[[ $fingerprint =~ ^[0-9a-f]{64}$ ]]

# Compare the signer with the current published release and reject reused version codes.
previous=$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases?per_page=100" \
  --jq '.[] | select(.draft == false and .prerelease == false) | .assets[] | select(.name | test("^LinkUp-Android-[1-9][0-9]{0,8}\\.apk$")) | .name + " " + .url' \
  | sort -t- -k3,3nr | sed -n '1p')
if [[ -n $previous ]]; then
  read -r asset api <<< "$previous"
  previous_code=${asset#LinkUp-Android-}
  [[ $code -gt ${previous_code%.apk} ]]
  [[ $api =~ ^https://api\.github\.com/repos/SDAkshevskij/Linkup/releases/assets/[0-9]+$ ]]
  gh api "${api#https://api.github.com/}" -H 'Accept: application/octet-stream' > "$private/previous.apk"
  [[ $(cert "$private/previous.apk") == "$fingerprint" ]]
fi
