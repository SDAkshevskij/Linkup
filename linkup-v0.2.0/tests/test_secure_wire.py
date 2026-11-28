"""Tests for the isolated v1 binary codec; parsing is not authentication."""
import random
import sys
import unittest
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'linux'))
import secure_wire as w

A = uuid.UUID('00112233-4455-6677-8899-aabbccddeeff')
R = uuid.UUID('10213243-5465-7687-98a9-bacbdcedfe0f')
D = uuid.UUID('ffeeddcc-bbaa-9988-7766-554433221100')
N = bytes(range(24))
HEADER = bytes.fromhex('01' + A.hex + R.hex + D.hex + '0000000000000007' + N.hex())


def fixture():
    return dict(line.split('=', 1) for line in Path(__file__).with_name(
        'secure_wire_v1.properties').read_text(encoding='ascii').splitlines()
        if line and not line.startswith('#'))


class WireTests(unittest.TestCase):
    def test_literal_header(self):
        self.assertEqual(w.Header(A, R, D, 7, N).encode(), HEADER)

    def test_canonical_assemble_and_untrusted_split(self):
        cipher, signature = bytes(range(16)), bytes(range(64))
        h = w.Header(A, R, D, 7, N)
        wire = w.assemble(h, cipher, signature)
        self.assertEqual(wire, HEADER + cipher + signature)
        self.assertEqual(w.split_untrusted(wire), (h, cipher, signature))
        self.assertEqual(w.signature_input(h, cipher), b'LinkUp/record/v1\0' + HEADER + cipher)

    def test_native_known_answer_framing(self):
        f = fixture()
        h, cipher, signature = w.split_untrusted(bytes.fromhex(f['wire']))
        self.assertEqual(h.encode(), bytes.fromhex(f['header']))
        self.assertEqual(cipher, bytes.fromhex(f['ciphertext']))
        self.assertEqual(signature, bytes.fromhex(f['signature']))
        self.assertEqual(w.signature_input(h, cipher), bytes.fromhex(f['signature_input']))
        self.assertEqual(w.assemble(h, cipher, signature), bytes.fromhex(f['wire']))

    def test_invalid_framing(self):
        wire = HEADER + bytes(80)
        cases = [wire[:i] for i in range(len(wire))]
        for offset in (1, 17, 33):
            cases.append(wire[:offset] + bytes(16) + wire[offset+16:])
        cases += [bytes([v]) + wire[1:] for v in (0, 2, 255)]
        cases += [wire[:49] + e.to_bytes(8, 'big') + wire[57:] for e in (0, 2**63, 2**64-1)]
        cases += [wire + bytes(2**20+1), bytearray(wire), None]
        for item in cases:
            with self.subTest(length=len(item) if item is not None else -1):
                with self.assertRaises(w.WireError):
                    w.split_untrusted(item)

    def test_invalid_header_inputs(self):
        for args in [(uuid.UUID(int=0), R, D, 1, N), (A, R, D, True, N),
                     (A, R, D, 0, N), (A, R, D, 2**63, N),
                     (A, R, D, 1, N[:-1]), (A, R, D, 1, bytearray(N))]:
            with self.subTest(args=args):
                with self.assertRaises(w.WireError):
                    w.Header(*args)

    def test_generated_roundtrip_and_literal_oracle(self):
        rng = random.Random(2731)
        for i in range(400):
            ids = [uuid.UUID(int=rng.randrange(1, 2**128)) for _ in range(3)]
            epoch = rng.choice([1, 2**63-1, rng.randrange(1, 2**63)])
            nonce = rng.randbytes(24)
            cipher, sig = rng.randbytes(16 + rng.randrange(0, 512)), rng.randbytes(64)
            raw = b'\x01' + b''.join(x.int.to_bytes(16, 'big') for x in ids) + epoch.to_bytes(8, 'big') + nonce
            h = w.Header(*ids, epoch, nonce)
            wire = w.assemble(h, cipher, sig)
            parsed = w.split_untrusted(wire)
            self.assertEqual(wire, raw + cipher + sig)
            self.assertEqual(parsed, (h, cipher, sig))

    def test_maximum_and_signature_input_bounds(self):
        h = w.Header(A, R, D, 1, N)
        cipher = bytes(2**20 + 16)
        signature = bytes(64)
        self.assertEqual(w.split_untrusted(w.assemble(h, cipher, signature))[1], cipher)
        for bad in (b'', bytes(15), cipher + b'\0', bytearray(cipher)):
            with self.assertRaises(w.WireError):
                w.signature_input(h, bad)
            with self.assertRaises(w.WireError):
                w.assemble(h, bad, signature)
        for bad_signature in (b'', bytes(63), bytes(65), bytearray(64)):
            with self.assertRaises(w.WireError):
                w.assemble(h, bytes(16), bad_signature)

    def test_signature_input_rejects_header_subclass(self):
        class ForgedHeader(w.Header):
            def encode(self):
                return bytes(w.HEADER_SIZE)
        forged = ForgedHeader(A, R, D, 7, N)
        with self.assertRaises(w.WireError):
            w.signature_input(forged, bytes(16))
        with self.assertRaises(w.WireError):
            w.assemble(forged, bytes(16), bytes(64))


if __name__ == '__main__':
    unittest.main()
