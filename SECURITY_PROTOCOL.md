# LinkUp E2EE v1 — draft wire contract (S1)

**Status:** isolated implementation slice, not enabled. Existing P0 containment remains unchanged. This document fixes record framing for review; enrollment, rotation, file manifests, persistent replay protection and transport integration are still blocking S1–S5. No production release or migration.

## Threat model and boundary

The server, disk providers and network observers may read, modify, reorder, duplicate, delay or drop traffic. Trusted devices must verify records locally using keys from a separately verified owner-signed roster, not keys supplied by a record or cloud response. A compromised trusted endpoint can read its own data; revocation cannot erase previously learned keys or content. An offline device cannot detect a withheld new roster; it must queue uploads until its current trust state can be checked. Server availability and traffic metadata (opaque IDs, ciphertext size and operational timing) are not protected. Use HTTPS/WSS with validated certificates, TLS 1.2 minimum, TLS 1.3 preferred. Local history remains plaintext under OS access controls; keys and tokens belong in protected OS storage. Do not send sensitive data before the gates pass.

## Record framing (normative for this draft)

All byte sequences are raw binary, not text. The 81-byte header consists of, in order:

| Offset | Bytes | Field |
|---:|---:|---|
| 0 | 1 | Version, exactly 1 |
| 1 | 16 | Account UUID in RFC network byte order |
| 17 | 16 | Record UUID in RFC network byte order |
| 33 | 16 | Sender device UUID in RFC network byte order |
| 49 | 8 | Epoch, unsigned big-endian, range 1..2^63-1 (portable signed database/Java limit) |
| 57 | 24 | Fresh random nonce from libsodium randombytes_buf |

Nil UUIDs are invalid. No restriction on UUID version/variant bits beyond nonzero. Header is the exact AEAD AAD. Account and epoch must match the receiver's current trusted state; sender ID must map to an independently trusted, epoch-authorized Ed25519 public key. Version, identifiers, lengths and epoch must be validated before attempting cryptography.

Derive a 32-byte record key with libsodium `crypto_kdf_derive_from_key`, ID 1, exactly 8 context bytes `LUrecord`, from the account epoch key. For file key wrapping use the distinct context `LUfilekw`, ID 1. Encrypt plaintext with `crypto_aead_xchacha20poly1305_ietf_encrypt` (32-byte key, 24-byte nonce, 16-byte tag); encrypted type, timestamps, names, contents and metadata must be inside plaintext payload. The ciphertext includes its tag. Sign `ASCII("LinkUp/record/v1") || 0x00 || header || ciphertext` with detached Ed25519. The complete wire is `header || ciphertext || signature` (64-byte signature). No trailing fields. A parser cannot authenticate by itself; it returns untrusted bytes. Verify detached signature before AEAD decryption; release neither plaintext nor side effects if any validation fails. Reject plaintext, unknown versions and malformed input; never fall back.

For this draft, record plaintext is capped at 1 MiB, excluding the tag. Envelope length must be 161..1,048,737 bytes. Oversize records stay local pending a supported file/segmentation path; never truncate or send plaintext. Decode any future transport wrapper with strict canonical encoding and size limits before allocation. Files use a separate encrypted secretstream with signed encrypted manifest as specified in TASKS.md; not implemented here.

## Receiver and lifecycle gates (not implemented)

Verify trust and signature and decrypt, then atomically deduplicate by account + record UUID in durable storage with application of the event. An exact retry is idempotent; a conflicting record with the same ID is rejected; do not mark a record seen before authentication or before committing its effect. Preserve tombstones/replay state across restarts. Store and enforce highest accepted epoch and roster. Enrollment must bind account, both devices/roles, both public key pairs and expiring challenge into out-of-band verification (at least 128-bit fingerprint) and proof of possession. Owner signature must cover the sealed key envelope and recipient/account/epoch/challenge; verify before unsealing. Only the owner-authorized writer can update membership; revoke/rotate using fresh epoch keys for remaining devices. No claim of forward secrecy, external audit or protection from withheld revocations.

## Reuse and dependency decision

Retain the native Android Java and Linux Python architecture. Use upstream libsodium primitives, not new cryptography. Reference https://doc.libsodium.org/key_derivation and https://doc.libsodium.org/secret-key_cryptography/aead/chacha20-poly1305/xchacha20-poly1305_construction and https://pynacl.readthedocs.io/en/latest/vectors/. Candidate sources: https://github.com/jedisct1/libsodium (ISC) and https://github.com/pyca/pynacl (Apache-2.0). The current isolated codecs use only standard libraries, no imported upstream code and no new runtime dependencies. The vector oracle uses installed libsodium, test only. Pinned upstream revisions, full license/dependency/security review and exact Android JNI/PyNaCl runtime bindings remain required before integration. No JavaScript crypto or alternative cipher.
