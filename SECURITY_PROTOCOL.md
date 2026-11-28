# LinkUp E2EE v1 — draft wire contract (S1)

**Status:** isolated framing and verification slice, not enabled. Existing P0 containment remains unchanged. Enrollment, rotation, protected key storage, authenticated runtime encryption/decryption, persistent replay protection, provider cutover and file secretstream remain blocking S1–S5. No production release or migration.

## Threat model and boundary

The server, disk providers and network observers may read, modify, reorder, duplicate, delay or drop traffic. Trusted devices must verify records locally using keys from a separately verified owner-signed roster, never keys supplied by a record or cloud response. A compromised trusted endpoint can read its own data; revocation cannot erase previously learned keys or content. An offline device cannot detect a withheld new roster; it must queue uploads until its current trust state can be checked. Server availability and traffic metadata such as opaque IDs, ciphertext size and operational timing are not protected. HTTPS/WSS with certificate validation remains mandatory in addition to E2EE. Local history remains plaintext under OS access controls; keys and authentication tokens belong in protected OS storage.

## Canonical record framing

All fields are raw binary. The header is exactly 81 bytes:

| Offset | Bytes | Field |
|---:|---:|---|
| 0 | 1 | Version, exactly 1 |
| 1 | 16 | Account UUID in RFC network byte order |
| 17 | 16 | Record UUID in RFC network byte order |
| 33 | 16 | Sender device UUID in RFC network byte order |
| 49 | 8 | Epoch, unsigned big-endian, accepted range 1..2^63-1 |
| 57 | 24 | Fresh random nonce from libsodium randombytes_buf |

Nil UUIDs are invalid. No UUID version/variant restriction is imposed beyond nonzero. Header is the exact AEAD AAD. Account and epoch must match trusted receiver state; sender ID must map to an independently trusted, epoch-authorized Ed25519 key. Version, identifiers, lengths and epoch are checked before cryptography.

The framing API intentionally distinguishes construction from parsing:
- **assemble** accepts exactly one Header, ciphertext including its 16-byte AEAD tag, and exactly one 64-byte detached signature and returns header || ciphertext || signature.
- **split_untrusted / splitUntrusted** performs structural validation only. Returned values are explicitly untrusted and must never reach application state before trust, signature and AEAD verification.
- **signature_input / signatureInput** returns ASCII("LinkUp/record/v1") || 0x00 || header || ciphertext.

No alternate wire builder, plaintext fallback or permissive parser is part of v1.

## Cryptographic operations

Derive the 32-byte record key with libsodium crypto_kdf_derive_from_key, subkey ID 1, context LUrecord, from the current 32-byte account epoch key. File-key wrapping uses the separate 8-byte context LUfilekw, also ID 1.

Encrypt record plaintext with crypto_aead_xchacha20poly1305_ietf_encrypt using the header as AAD, a fresh 24-byte random nonce and the derived record key. Sensitive type, timestamps, names, contents and metadata live inside ciphertext. Sign the domain-separated signature input with detached Ed25519. The receiver verifies trust and detached signature before AEAD decryption and releases neither plaintext nor side effects on any failure.

Record plaintext is capped at 1 MiB in v1, excluding the 16-byte tag. Envelope length is therefore 161..1,048,737 bytes. Oversize records stay local pending the file/segmentation path; they are never truncated or downgraded. File payloads use the separate secretstream design in TASKS.md and are not implemented here.

## Receiver/lifecycle gates still required

The authenticated runtime boundary must atomically deduplicate account + record UUID together with application of the decrypted event. Exact retry is idempotent; conflicting reuse of the same ID is rejected. Replay/tombstone state survives restart. Highest accepted epoch and roster are durable and stale known state is rejected.

Enrollment must bind account, both device identities and roles, both X25519/Ed25519 public keys and an expiring one-time challenge into at least a 128-bit out-of-band transcript fingerprint and proof of possession. Owner signature covers recipient/account/epoch/challenge-bound sealed-key envelope. Verify before unsealing. Only the owner-authorized writer changes membership. Revocation rotates to a fresh epoch key for remaining devices. This is stored-history encryption, not a forward-secret ratchet.

## Verification and upstream reuse

| Component | Decision | Evidence / source | License / status |
|---|---|---|---|
| Wire framing | Build small native codecs | Python struct/UUID and Java ByteBuffer/UUID only | Project code; no upstream source copied |
| KDF/AEAD/signature primitives | Adopt libsodium APIs mandated by TASKS.md | Official docs plus libsodium revision 8524e1f3129409416e66cb27c921d44fb70b4c87 | ISC; runtime pin/binding still pending |
| KDF oracle | Reuse expected test output | test/default/kdf.c and kdf.exp at pinned revision | ISC test-vector data |
| XChaCha oracle | Reuse expected test output | test/default/aead_xchacha20poly1305.c and .exp at pinned revision | ISC test-vector data |
| Ed25519 oracle | Reuse RFC 8032 vector 1 | RFC 8032 | Standard test vector |
| Linux runtime binding | Evaluate PyNaCl + narrow libsodium binding where needed | pyca/pynacl | Not integrated or pinned yet |
| Android runtime binding | Evaluate pinned libsodium NDK/JNI or maintained exact-API binding | jedisct1/libsodium | Not integrated or pinned yet |

The synthetic LinkUp fixture is checked only after the loaded libsodium passes external KDF/XChaCha known-answer vectors and RFC 8032 Ed25519. The fixture then checks LinkUp domain separation, AAD, detached signatures, wrong key/signature rejection and tamper rejection. This does not constitute an external audit of the assembled protocol.

See DIFFERENTIAL_REVIEW_REPORT.md and S1_HANDOFF.md for review findings, test evidence and remaining gates.
