# Active tasks

## P0 — get a tested Android APK
- [x] Import v0.2.0 sources and connect GitHub.
- [x] Correct root workflow paths for the nested project directory.
- [x] Initialize SDK tools and explicitly select supported SDK packages.
- [x] Confirm JS tests (7 passed) and debug APK assembly on [CI run 46025692710](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710).
- [x] Fix `MissingPermission` for cached location and `ProtectedPermissions` for the Usage Access declaration. Lint reports 0 errors and 11 warnings. [CI diagnostics](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211043497)
- [x] Run `gradle :app:connectedDebugAndroidTest` on emulator API 35. Both smoke tests passed. [CI diagnostics](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211043497)
- [x] Download the tested `app-debug.apk` and verify its debug signature with `apksigner verify --verbose`. [APK artifact](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211278532)

## P1 — follow-up
- [ ] Persistent signing key and safe upgrade path (owner-controlled secret setup if required).
- [ ] Real Linux Mint X11 runtime/package verification; Wayland deferred.
- [ ] End-to-end transfer and activity sync with test accounts; actual Android permission/GPS/background checks.
- [ ] Improve background Transfer and OAuth renewal; assess local credential storage and encryption before relying on sensitive data.

After completing an item, record evidence in STATUS.md and CHANGELOG.md. Do not mark an unexecuted test as passed.
