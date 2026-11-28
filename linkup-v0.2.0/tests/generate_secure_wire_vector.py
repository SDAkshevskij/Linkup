"""Independent test-only libsodium oracle; fixed keys/nonces are synthetic, NEVER use in production.

Run explicitly: python3 tests/generate_secure_wire_vector.py --check
Requires a system libsodium exposing the mandated APIs. Does not import the codec.
"""
import argparse
import ctypes as C
import ctypes.util
from pathlib import Path


FIXTURE = Path(__file__).with_name('secure_wire_v1.properties')
P = C.c_void_p
U = C.c_ulonglong
S = C.c_size_t


def vector():
    name = ctypes.util.find_library('sodium')
    if not name:
        raise RuntimeError('test oracle needs system libsodium')
    lib = C.CDLL(name)

    def fn(name, args, restype=C.c_int):
        f = getattr(lib, name)
        f.argtypes, f.restype = args, restype
        return f

    def check(code):
        if code != 0:
            raise RuntimeError('libsodium oracle operation failed')

    if fn('sodium_init', [])() < 0:
        raise RuntimeError('libsodium initialization failed')
    version = fn('sodium_version_string', [], C.c_char_p)().decode('ascii')
    derive = fn('crypto_kdf_derive_from_key', [P, S, C.c_uint64, P, P])
    sign_keypair = fn('crypto_sign_seed_keypair', [P, P, P])
    encrypt = fn('crypto_aead_xchacha20poly1305_ietf_encrypt', [P, C.POINTER(U), P, U, P, U, P, P, P])
    decrypt = fn('crypto_aead_xchacha20poly1305_ietf_decrypt', [P, C.POINTER(U), P, P, U, P, U, P, P])
    sign = fn('crypto_sign_detached', [P, C.POINTER(U), P, U, P])
    verify = fn('crypto_sign_verify_detached', [P, P, U, P])

    master, seed, nonce = bytes(range(32)), bytes(range(32, 64)), bytes(range(24))
    header = bytes.fromhex('0100112233445566778899aabbccddeeff102132435465768798a9bacbdcedfe0f'
                           'ffeeddccbbaa998877665544332211000000000000000007000102030405060708090a0b0c0d0e0f1011121314151617')
    payload = 'LinkUp synthetic: Привет'.encode('utf-8')
    key, file_key = C.create_string_buffer(32), C.create_string_buffer(32)
    check(derive(key, 32, 1, b'LUrecord', master))
    check(derive(file_key, 32, 1, b'LUfilekw', master))
    if key.raw == file_key.raw:
        raise RuntimeError('KDF domain separation failure')
    public, secret = C.create_string_buffer(32), C.create_string_buffer(64)
    check(sign_keypair(public, secret, seed))
    ciphertext, length = C.create_string_buffer(len(payload) + 16), U()
    check(encrypt(ciphertext, C.byref(length), payload, len(payload), header, len(header), None, nonce, key))
    if length.value != len(ciphertext.raw):
        raise RuntimeError('unexpected tag length')
    signed = b'LinkUp/record/v1\0' + header + ciphertext.raw
    signature, siglen = C.create_string_buffer(64), U()
    check(sign(signature, C.byref(siglen), signed, len(signed), secret))
    if siglen.value != 64:
        raise RuntimeError('unexpected signature length')
    check(verify(signature, signed, len(signed), public))
    output, outlen = C.create_string_buffer(len(payload)), U()
    check(decrypt(output, C.byref(outlen), None, ciphertext, length.value, header, len(header), nonce, key))
    if outlen.value != len(payload) or output.raw != payload:
        raise RuntimeError('plaintext mismatch')
    # Each altered byte of the signed record must be rejected; AEAD independently rejects changed AAD, tag or key.
    for i in range(len(signed)):
        bad = bytearray(signed); bad[i] ^= 1
        if verify(signature, bytes(bad), len(bad), public) == 0:
            raise RuntimeError('modified signed bytes accepted')
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
    return version, '# Synthetic test vector; fixed keys/nonces must never be used in production.\n' + ''.join(
        name + '=' + value.hex() + '\n' for name, value in fields.items())


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
    print(f'libsodium {version}: KDF, AEAD, Ed25519, tamper checks and vector verified')


if __name__ == '__main__':
    main()
