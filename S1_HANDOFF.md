# S1 handoff — record framing draft

Base: main 6937e0b2c380da32a936016a62996da93b8eb674. Work remains isolated in draft PR #6; no production route, release or cloud gate has been enabled.

## What was re-reviewed and corrected

A second security-focused review found five defects in the first draft:
1. SecureWireTest.java was not executed by GitHub Actions.
2. generate_secure_wire_vector.py was not executed by GitHub Actions.
3. The LinkUp fixture was generated and checked by the same oracle without first anchoring that oracle to external known-answer vectors.
4. The codec could parse a wire but had no canonical outbound assembler; future callers would have to concatenate crypto fields manually. The parser name also did not make its unauthenticated status obvious.
5. Python signature_input accepted Header subclasses, allowing an overridden encode() to violate canonical signature bytes. The required differential-review report file was also missing.

The branch now uses assemble + split_untrusted/splitUntrusted, exact signature sizing, strict Python Header type at the canonical boundary, and CI steps for the JVM codec and libsodium oracle. The oracle first checks a pinned libsodium KDF KAT, a pinned libsodium XChaCha20-Poly1305 KAT and RFC 8032 Ed25519 vector 1 before checking the synthetic LinkUp fixture.

## Fresh local evidence

Commands executed against the rewritten files:
- python3 -m unittest discover -s tests -p 'test_secure_wire.py' -v — 8 passed, including 400 generated round trips, boundaries, canonical assembly and the Header-subclass regression.
- python3 tests/generate_secure_wire_vector.py --check — passed on system libsodium 1.0.18: upstream KDF/XChaCha KATs, RFC8032, LinkUp fixture, tamper, wrong signature and wrong public key.
- javac --release 17 ... SecureWire.java tests/SecureWireTest.java && java ... SecureWireTest tests/secure_wire_v1.properties — passed, including 400 generated cases, immutable snapshots, canonical assembly and size/rejection cases.

RED evidence from this review:
- The new Python assemble regression initially errored because assemble did not exist.
- The forged Header regression initially failed because signature_input accepted the subclass.
- The new JVM review test initially failed compilation because assemble, UntrustedParts and splitUntrusted did not exist.
These then passed after the minimal production changes.

GitHub Actions for code head `976a16f29921491a3228ec810a5d8177a4f8b66e` completed successfully: both Linux and Android jobs passed, including the new JVM framing test, libsodium oracle, existing PostgreSQL/X11 suite, Android build/lint, emulator and upgrade regressions. [CI run 46147328214](https://github.com/SDAkshevskij/Linkup/actions/runs/46147328214).

## Open Source First decisions

| Subtask | Decision | Exact source | Reason |
|---|---|---|---|
| Framing | Build/retain | Standard-library Python/Java only | Small deterministic surface; dependency would add risk |
| KDF and XChaCha test anchors | Adapt expected vectors only | libsodium 8524e1f3129409416e66cb27c921d44fb70b4c87, test/default/kdf.*, test/default/aead_xchacha20poly1305.* | Independent call-shape/result check; ISC |
| Ed25519 anchor | Reuse test vector | RFC 8032 vector 1 | Independent standard vector |
| Production crypto | Adopt libsodium primitive APIs | libsodium, exact runtime pin pending | Mandated protocol primitives; do not reimplement |
| Linux binding | Evaluate PyNaCl plus narrow binding | pyca/pynacl | Pending dependency/security review |
| Android binding | Evaluate libsodium NDK/JNI/exact binding | jedisct1/libsodium | Pending dependency/security review |

No production runtime dependency was added in this slice. CI installs distro libsodium only for the test oracle.

## Skills applied

- systematic-debugging: traced the missing CI boundaries and canonicalization issue before changes; root causes recorded above.
- test-driven-development: added failing assemble/subclass/JVM API tests first, observed RED, then rewrote the codecs.
- property-based-testing guidance: retained deterministic generated invariants without adding a new PBT dependency; no claim of shrinking/Hypothesis.
- differential-review: high-risk review artifact is DIFFERENTIAL_REVIEW_REPORT.md; production blast radius remains zero because there are no runtime call sites.
- verification-before-completion: local claims are limited to commands above; CI/device/runtime crypto claims remain separate.

## Remaining gates

| Case | State |
|---|---|
| Canonical framing, malformed/bounds, external KATs, synthetic fixture | Implemented; code head 976a16f passed [CI 46147328214](https://github.com/SDAkshevskij/Linkup/actions/runs/46147328214) |
| Linux PyNaCl/native authenticated encrypt/decrypt | Pending |
| Android libsodium JNI/native authenticated encrypt/decrypt | Pending |
| Trusted sender/epoch lookup before decryption | Pending |
| Durable replay/dedup transaction | Pending |
| S2 enrollment, proof of possession, key storage, revocation/rotation | Pending |
| S3 secretstream files and all content routing | Pending |
| S4 encrypted schema/provider cutover and plaintext rejection | Pending |
| S5 cross-device/offline/restart/tamper/provider inspection | Pending |
| Physical Android/Mint P0 checks | Pending owner/device validation |

## Next actionable step

Do not merge or enable this draft merely because framing CI is green. Next implement the authenticated record boundary on Linux and Android using pinned reviewed libsodium bindings: trusted account/sender/epoch lookup -> Ed25519 verification -> XChaCha20-Poly1305 decryption -> atomic replay/dedup + application effect. Add cross-platform crypto round trips and malformed/wrong-key/replay tests before wiring any cloud transport.
