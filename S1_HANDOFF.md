# S1 handoff — record framing draft

Base inspected through GitHub: `SDAkshevskij/Linkup` main at `6937e0b2c380da32a936016a62996da93b8eb674`; P0 implementation CI run [46130074791](https://github.com/SDAkshevskij/Linkup/actions/runs/46130074791) succeeded (historical, not this change).

## Implemented and tested
- SECURITY_PROTOCOL.md specifies a bounded canonical v1 record header, signature input and threat model; 1 MiB record cap and positive signed-long-compatible epochs are proposed interoperability decisions.
- Native-platform framing codecs in Linux Python and Android Java. No production call sites, network requests, dependency changes, migrations, UI changes or changes to P0 containment. Parsing never authenticates a message.
- Shared synthetic known-answer fixture using system libsodium 1.0.18: KDF contexts, XChaCha20-Poly1305-IETF, Ed25519 signatures; direct native oracle checks changed signed bytes, AAD, tag and key. Fixed fixture keys/nonces are not for production.
- Fresh isolated test evidence: `cd linkup-v0.2.0 && python -m unittest discover -s tests -p test_secure_wire.py -v` — 7 passed (400 seeded roundtrips plus boundary/rejection cases). `python tests/generate_secure_wire_vector.py --check` — passed against libsodium 1.0.18. `javac --release 17 -d /tmp/linkup-jvm android/app/src/main/java/io/linkup/app/security/SecureWire.java tests/SecureWireTest.java && java -cp /tmp/linkup-jvm SecureWireTest` — passed (400 seeded cases plus literal/fixture, malformed, immutability and size limits).
- RED evidence: test suite was written before implementation, initially failed to import missing Python module / compile missing Java class; then passed. This is initial missing-feature evidence, not a fully observed red/green cycle for every edge case.

## Skills and differential review
- test-driven-development: test-first fixtures and boundary tests, explicit red/green evidence above.
- property-based-testing: seeded generators and independent byte-order oracle; no new dependencies. No claim of shrinking or Hypothesis coverage.
- differential-review: risk HIGH because format will be used at a cryptographic trust boundary. Reviewed new code for size bounds, UUID/epoch validation, immutable Java output, exact AAD/signature domain and no downgrade. There are zero production call sites in the new slice. No baseline safety code removed. Replay, trusted key lookup, protected storage, malformed payload handling and stale roster behavior are intentionally unimplemented; enabling sync is blocked. No external audit. Full repo blame/call graph and regression review not possible without a checkout. No known live exploit findings are published here.
- verification-before-completion: scoped claims only; full repo suite, Android SDK/lint/emulator, actual PyNaCl/NDK binding, CI for this exact head, physical phone and Mint validation not run. Direct Git clone failed DNS in this environment; source archives could not be materialized. Source files and backlog were inspected via GitHub connector instead.
- Open Source First: official libsodium and PyNaCl references consulted; test oracle uses installed libsodium 1.0.18 only. No upstream source copied; no production dependency pin or full audit completed. Keep mandated APIs and pin reviewed stable dependencies before runtime integration.

## Test matrix and outstanding gates
| Case | Evidence |
|---|---|
| Empty and max-length ciphertext framing; invalid version, UUID, epoch, nonce, truncation below minimum and oversized input | Python and JVM tests |
| Canonical byte order, domain separation, shared native fixture, seeded variations, mutable input/output | Python/JVM tests; immutable Java snapshot |
| Modified AAD/tag/key and all changed signature-input bytes | Native fixture oracle, not app integration |
| S1 actual Linux PyNaCl and Android JNI encrypt/decrypt, upstream KAT, parser auth order | Pending |
| S2 trust enrollment, expiry, revocation, protected keys, rotation, stale rosters | Pending |
| S3 file chunks, final tag, interrupted stream, size and retry, cloud metadata | Pending |
| S4 replay/dedup transaction, cloud cutover, rejection of plaintext routes, RLS | Pending |
| S5 cross-device, restart/offline, malformed payload, wrong key/account, provider errors, actual outgoing requests | Pending |
| Physical-device P0-A/C and live SQL P0-B | Owner/device/deployment actions; pending |
| Local keyboard, clipboard, Activity redesign | Not implemented in this slice |

## Next actionable step for Codex
Review the proposed wire limits and exact framing. Use a fresh checkout of latest main and a dedicated branch. Pin/audit libsodium and PyNaCl; implement the native authenticated record boundary with trusted sender/epoch checks, never return parsed-but-unverified data to the app. Add runtime crypto integration tests against the fixture, independent upstream known-answer vectors, and full CI. Then implement S2–S5 in dependency order. Keep all existing gates and no release without explicit approval. Do not mark any S1–S5 task complete from this patch.
