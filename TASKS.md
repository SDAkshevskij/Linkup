# Active tasks

## P0 — get a tested Android APK
- [x] Import v0.2.0 sources and connect GitHub.
- [x] Correct root workflow paths for the nested project directory.
- [x] Initialize SDK tools and explicitly select supported SDK packages.
- [x] Confirm JS tests 7/7 and :app:assembleDebug succeeded on CI run 46024155107.
- [ ] Verify both Android Lint fixes on branch `fix/android-ci`. [Baseline CI report](https://github.com/SDAkshevskij/Linkup/actions/runs/46024155107/artifacts/15211301490) identified `MissingPermission` for cached location and `ProtectedPermissions` for the Usage Access declaration.
- [ ] Run Android instrumentation tests on emulator API 35. Diagnose and fix failures; retain logs and screenshots where helpful.
- [ ] Verify and link the tested APK artifact only after successful CI.

## P1 — follow-up
- [ ] Persistent signing key and safe upgrade path (owner-controlled secret setup if required).
- [ ] Real Linux Mint X11 runtime/package verification; Wayland deferred.
- [ ] End-to-end transfer and activity sync with test accounts; actual Android permission/GPS/background checks.
- [ ] Improve background Transfer and OAuth renewal; assess local credential storage and encryption before relying on sensitive data.

After completing an item, record evidence in STATUS.md and CHANGELOG.md. Do not mark an unexecuted test as passed.
