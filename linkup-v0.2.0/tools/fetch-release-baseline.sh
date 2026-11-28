#!/usr/bin/env bash
set -euo pipefail
: "${RUNNER_TEMP:?}"
out="$RUNNER_TEMP/linkup-v0.2.1"
mkdir "$out"
gh release download v0.2.1 --repo SDAkshevskij/Linkup --dir "$out" \
  --pattern 'LinkUp-Android-3.apk' --pattern 'LinkUp-Linux-3.zip'
(
  cd "$out"
  sha256sum -c <<'SUMS'
7ec9094e3f256734252541d1591c8dce06dcec5256a3d409e61c0c333cc6e70b  LinkUp-Android-3.apk
250da058ec0f7625885bbacb9fb02c77d505b8c3b45a821da80f53762f5e73e4  LinkUp-Linux-3.zip
SUMS
)
