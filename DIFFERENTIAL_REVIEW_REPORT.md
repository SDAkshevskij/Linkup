# Differential security review — PR #6 S1 framing

## Executive summary

**Scope:** draft PR #6 relative to main 6937e0b.  
**Risk:** HIGH because the changed code defines bytes that will later sit on a cryptographic trust boundary.  
**Current recommendation:** CONDITIONAL / keep draft. The framing slice can be reviewed and tested, but S1 is not complete and no cloud path may consume it yet.

| Severity | Found in first draft | Status after rework |
|---|---:|---|
| Critical | 0 | — |
| High | 0 | — |
| Medium | 3 | Resolved in draft |
| Low | 2 | Resolved in draft |

Production blast radius is currently zero: no Transfer, Activity, clipboard, provider or native collector call site invokes SecureWire/secure_wire. This lowers immediate exploitability but does not lower the review standard for the future boundary.

## Findings

### MEDIUM — New Java codec test was outside CI

SecureWireTest.java existed only as a manual javac command. Android Gradle reported NO-SOURCE for JVM tests, so a green workflow did not prove the Java framing codec or shared fixture.

**Fix:** add a dedicated Java 17 compile/run step to .github/workflows/android.yml.

### MEDIUM — Crypto oracle was outside CI

The native libsodium oracle and fixture check were manual only. A changed fixture or ctypes call signature could therefore evade ordinary PR CI.

**Fix:** install the distro libsodium runtime in the Linux CI job and execute generate_secure_wire_vector.py --check before the Linux suite.

### MEDIUM — Synthetic fixture was self-anchored

The same script generated and checked the LinkUp fixture. That detects drift but cannot establish that the oracle itself was initially wired correctly.

**Fix:** before touching the LinkUp fixture, validate the loaded library against:
- libsodium KDF expected output from revision 8524e1f3129409416e66cb27c921d44fb70b4c87 test/default/kdf.c + kdf.exp;
- libsodium XChaCha20-Poly1305 expected output from the same revision test/default/aead_xchacha20poly1305.c + .exp;
- RFC 8032 Ed25519 test vector 1.

### LOW — No canonical outbound wire builder / ambiguous parser API

The first codec exposed split/Parts but no assembly function, leaving future senders to concatenate header, ciphertext and signature ad hoc. Parts also did not make the pre-authentication state obvious.

**Fix:** add assemble with exact ciphertext/signature bounds and rename parsing to split_untrusted/splitUntrusted with UntrustedParts on Java.

### LOW — Python canonical signature input accepted Header subclasses

signature_input used isinstance(header, Header). A subclass could override encode() and produce bytes that were not the canonical 81-byte header.

**Fix:** require the exact Header type at canonical assembly/signature boundaries. Java Header was already final.

## Adversarial analysis

An attacker cannot reach this new code through a production network path yet. The future threat is a malicious cloud intermediary supplying arbitrary wire bytes. The structural parser therefore must only bound/parse; it must never be treated as authentication. The authenticated runtime boundary still needs to enforce trusted account, sender and epoch, verify detached Ed25519 before AEAD decrypt, and atomically replay-deduplicate before side effects.

The current draft deliberately does not implement that boundary. Enabling cloud sync with only this framing code would be a security failure.

## Test coverage after rework

Local fresh evidence:
- Python: 8 framing tests, 400 deterministic generated cases.
- JVM: literal/fixture/bounds/immutability plus 400 deterministic generated cases.
- Native oracle: upstream KDF KAT, upstream XChaCha KAT, RFC8032 Ed25519, LinkUp KDF domain separation, modified signed bytes, modified signature, wrong public key, AAD/tag/key tampering.

CI is updated to execute the JVM test and native oracle. Exact-head workflow results must be checked before approval.

## Historical/baseline context

No existing security check, validation or containment code was removed. P0 plaintext containment remains unchanged. No runtime dependency, network route, schema migration or release workflow was enabled by the codec itself.

## Remaining blocking risks

- No pinned production libsodium binding on Android.
- No reviewed PyNaCl/narrow binding runtime on Linux.
- No OS-protected key storage.
- No S2 trust roster/enrollment/rotation.
- No persistent replay/dedup transaction.
- No S3 file secretstream or full content routing.
- No S4 encrypted cloud cutover.
- No S5 cross-platform/device/provider evidence.

## Methodology and limits

Applied systematic debugging, TDD, generated-invariant testing, differential review and verification-before-completion. Review covered every changed security/code/test file in PR #6 plus the CI boundary and S1 acceptance contract. External crypto primitives were not reimplemented or formally audited. Physical devices and future runtime crypto bindings are outside this isolated framing slice.
