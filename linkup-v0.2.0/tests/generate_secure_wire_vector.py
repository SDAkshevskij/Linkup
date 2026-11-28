"""Independent test-only libsodium oracle for LinkUp E2EE v1.

The checked-in LinkUp fixture is synthetic. Before creating/checking it, this script
first checks the loaded libsodium API against external known-answer vectors:
- libsodium test/default/kdf.c + kdf.exp at revision
  8524e1f3129409416e66cb27c921d44fb70b4c87 (ISC)
- libsodium test/default/aead_xchacha20poly1305.c + .exp at that revision (ISC)
- RFC 8032 Ed25519 test vector 1

Only expected test-vector bytes are reproduced from upstream; no upstream production
code is copied. Fixed keys/nonces below are test data and MUST NOT be used in production.
"""
import argparse
import ctypes as C
import ctypes.util
from pathlib import Path

FIXTURE = Path(__file__).with_name('secure_wire_v1.properties')
P = C.c_void_p
U = C.c_ulonglong
S = C.c_size_t

UPSTREAM_KDF64_ID0 = bytes.fromhex(
    'a0c724404728c8bb95e5433eb6a9716171144d61efb23e74b873fcbeda51d807'
    '1b5d70aae12066dfc94ce943f145aa176c055040c3dd73b0a15e36254d450614')
UPSTREAM_XCHACHA = bytes.fromhex(
    'f8ebea4875044066fc162a0604e171feecfb3d20425248563bcfd5a155dcc47b'
    'bda70b86e5ab9b55002bd1274c02db35321acd7af8b2e2d25015e136b7679458'
    'e9f43243bf719d639badb5feac03f80a19a96ef10cb1d15333a837b90946ba38'
    '54ee74da3f2585efc7e1e170e17e15e563e77601f4f85cafa8e5877614e143e6'
    '8420')
RFC8032_SEED = bytes.fromhex('9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60')
RFC8032_PUBLIC = bytes.fromhex('d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a')
RFC8032_SIGNATURE = bytes.fromhex(
    'e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155'
    '5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b')


def vector():
    name = ctypes.util.find_library('sodium')
    if not name:
        raise RuntimeError('test oracle needs system libsodium')
    lib = C.CDLL(name)

    def fn(symbol, args, restype=C.c_int):
        f = getattr(lib, symbol)
        f.argtypes, f.restype = args, restype
        return f

    def check(code, label):
        if code != 0:
            raise RuntimeError(f'libsodium operation failed: {label}')

    if fn('sodium_init', [])() < 0:
        raise RuntimeError('libsodium initialization failed')
    version = fn('sodium_version_string', [], C.c_char_p)().decode('ascii')
    derive = fn('crypto_kdf_derive_from_key', [P, S, C.c_uint64, P, P])
    sign_keypair = fn('crypto_sign_seed_keypair', [P, P, P])
    encrypt = fn('crypto_aead_xchacha20poly1305_ietf_encrypt', [P, C.POINTER(U), P, U, P, U, P, P, P])
    decrypt = fn('crypto_aead_xchacha20poly1305_ietf_decrypt', [P, C.POINTER(U), P, P, U, P, U, P, P])
    sign = fn('crypto_sign_detached', [P, C.POINTER(U), P, U, P])
    verify = fn('crypto_sign_verify_detached', [P, P, U, P])

    kdf_out = C.create_string_buffer(64)
    check(derive(kdf_out, 64, 0, b'KDF test', bytes(range(32))), 'upstream KDF KAT')
    if kdf_out.raw != UPSTREAM_KDF64_ID0:
        raise RuntimeError('upstream KDF known-answer mismatch')

    x_key = bytes(range(0x80, 0xA0))
    x_nonce = bytes.fromhex('07000000404142434445464748494a4b4c4d4e4f50515253')
    x_ad = bytes.fromhex('50515253c0c1c2c3c4c5c6c7')
    x_msg = b"Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it."
    x_cipher = C.create_string_buffer(len(x_msg) + 16)
    x_len = U()
    check(encrypt(x_cipher, C.byref(x_len), x_msg, len(x_msg), x_ad, len(x_ad), None, x_nonce, x_key), 'upstream XChaCha KAT')
    if x_len.value != len(UPSTREAM_XCHACHA) or x_cipher.raw != UPSTREAM_XCHACHA:
        raise RuntimeError('upstream XChaCha known-answer mismatch')

    rfc_public, rfc_secret = C.create_string_buffer(32), C.create_string_buffer(64)
    check(sign_keypair(rfc_public, rfc_secret, RFC8032_SEED), 'RFC8032 keypair')
    if rfc_public.raw != RFC8032_PUBLIC:
        raise RuntimeError('RFC8032 public-key mismatch')
    rfc_sig, rfc_siglen = C.create_string_buffer(64), U()
    empty = C.create_string_buffer(1)
    check(sign(rfc_sig, C.byref(rfc_siglen), empty, 0, rfc_secret), 'RFC8032 sign')
    if rfc_siglen.value != 64 or rfc_sig.raw != RFC8032_SIGNATURE:
        raise RuntimeError('RFC8032 signature mismatch')
    check(verify(rfc_sig, empty, 0, rfc_public), 'RFC8032 verify')

    master, seed, nonce = bytes(range(32)), bytes(range(32, 64)), bytes(range(24))
    header = bytes.fromhex(
        '0100112233445566778899aabbccddeeff102132435465768798a9bacbdcedfe0f'
        'ffeeddccbbaa998877665544332211000000000000000007000102030405060708090a0b0c0d0e0f1011121314151617')
    payload = 'LinkUp synthetic: Привет'.encode('utf-8')
    key, file_key = C.create_string_buffer(32), C.create_string_buffer(32)
    check(derive(key, 32, 1, b'LUrecord', master), 'LinkUp record KDF')
    check(derive(file_key, 32, 1, b'LUfilekw', master), 'LinkUp file-wrap KDF')
    if key.raw == file_key.raw:
        raise RuntimeError('KDF domain separation failure')

    public, secret = C.create_string_buffer(32), C.create_string_buffer(64)
    check(sign_keypair(public, secret, seed), 'LinkUp signing keypair')
    ciphertext, length = C.create_string_buffer(len(payload) + 16), U()
    check(encrypt(ciphertext, C.byref(length), payload, len(payload), header, len(header), None, nonce, key), 'LinkUp encrypt')
    if length.value != len(ciphertext.raw):
        raise RuntimeError('unexpected tag length')

    signed = b'LinkUp/record/v1\0' + header + ciphertext.raw
    signature, siglen = C.create_string_buffer(64), U()
    check(sign(signature, C.byref(siglen), signed, len(signed), secret), 'LinkUp sign')
    if siglen.value != 64:
        raise RuntimeError('unexpected signature length')
    check(verify(signature, signed, len(signed), public), 'LinkUp verify')

    output, outlen = C.create_string_buffer(len(payload)), U()
    check(decrypt(output, C.byref(outlen), None, ciphertext, length.value, header, len(header), nonce, key), 'LinkUp decrypt')
    if outlen.value != len(payload) or output.raw != payload:
        raise RuntimeError('plaintext mismatch')

    for i in range(len(signed)):
        bad = bytearray(signed)
        bad[i] ^= 1
        if verify(signature, bytes(bad), len(bad), public) == 0:
            raise RuntimeError('modified signed bytes accepted')
    bad_signature = bytearray(signature.raw)
    bad_signature[0] ^= 1
    if verify(bytes(bad_signature), signed, len(signed), public) == 0:
        raise RuntimeError('modified signature accepted')
    wrong_public, wrong_secret = C.create_string_buffer(32), C.create_string_buffer(64)
    check(sign_keypair(wrong_public, wrong_secret, bytes(range(64, 96))), 'wrong keypair')
    if verify(signature, signed, len(signed), wrong_public) == 0:
        raise RuntimeError('wrong public key accepted')

    for bad_cipher, bad_header, bad_key in (
        (ciphertext.raw[:-1] + bytes([ciphertext.raw[-1] ^ 1]), header, key),
        (ciphertext.raw, header[:1] + bytes([header[1] ^ 1]) + header[2:], key),
        (ciphertext.raw, header, file_key),
    ):
        if decrypt(output, C.byref(outlen), None, bad_cipher, len(bad_cipher), bad_header, len(bad_header), nonce, bad_key) == 0:
            raise RuntimeError('modified AEAD inputs accepted')

    fields = dict(master=master, signing_seed=seed, nonce=nonce, header=header, plaintext=payload,
                  record_key=key.raw, file_wrap_key=file_key.raw, signing_public=public.raw,
                  ciphertext=ciphertext.raw, signature_input=signed, signature=signature.raw,
                  wire=header + ciphertext.raw + signature.raw)
    contents = '# Synthetic test vector; fixed keys/nonces must never be used in production.\n' + ''.join(
        field + '=' + value.hex() + '\n' for field, value in fields.items())
    return version, contents


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--check', action='store_true', help='verify the checked-in fixture without writing')
    args = p.parse_args()
    version, contents = vector()
    if args.check:
        if FIXTURE.read_text(encoding='ascii') != contents:
            raise SystemExit('fixture mismatch')
    else:
        FIXTURE.write_text(contents, encoding='ascii')
    print(f'libsodium {version}: upstream KDF/XChaCha KATs, RFC8032, LinkUp vector and tamper checks verified')


if __name__ == '__main__':
    main()
