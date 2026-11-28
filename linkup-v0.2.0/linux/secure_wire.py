"""Canonical structural framing for LinkUp E2EE v1.

This module does not authenticate records. Callers must verify trust and the detached
signature before decrypting or causing side effects.
"""
from dataclasses import dataclass
import struct
from uuid import UUID

VERSION = 1
MAX_PLAINTEXT = 1 << 20
TAG_SIZE = 16
SIGNATURE_SIZE = 64
NONCE_SIZE = 24
DOMAIN = b'LinkUp/record/v1\0'
_HEADER = struct.Struct('>B16s16s16sQ24s')
HEADER_SIZE = _HEADER.size
MIN_CIPHERTEXT_SIZE = TAG_SIZE
MAX_CIPHERTEXT_SIZE = MAX_PLAINTEXT + TAG_SIZE
MIN_WIRE_SIZE = HEADER_SIZE + MIN_CIPHERTEXT_SIZE + SIGNATURE_SIZE
MAX_WIRE_SIZE = HEADER_SIZE + MAX_CIPHERTEXT_SIZE + SIGNATURE_SIZE


class WireError(ValueError):
    """Malformed or unsupported framing; never a reason to downgrade."""


@dataclass(frozen=True)
class Header:
    account: UUID
    record: UUID
    sender: UUID
    epoch: int
    nonce: bytes

    def __post_init__(self):
        if any(type(value) is not UUID or value.int == 0
               for value in (self.account, self.record, self.sender)):
            raise WireError('invalid identifier')
        if type(self.epoch) is not int or not 1 <= self.epoch < 2**63:
            raise WireError('invalid epoch')
        if type(self.nonce) is not bytes or len(self.nonce) != NONCE_SIZE:
            raise WireError('invalid nonce')

    def encode(self) -> bytes:
        return _HEADER.pack(VERSION, self.account.bytes, self.record.bytes,
                            self.sender.bytes, self.epoch, self.nonce)


def _require_ciphertext(ciphertext: bytes) -> None:
    if type(ciphertext) is not bytes or not MIN_CIPHERTEXT_SIZE <= len(ciphertext) <= MAX_CIPHERTEXT_SIZE:
        raise WireError('invalid ciphertext size or type')


def assemble(header: Header, ciphertext: bytes, signature: bytes) -> bytes:
    """Build the only canonical v1 wire representation from already-produced crypto bytes."""
    if type(header) is not Header:
        raise WireError('invalid header')
    _require_ciphertext(ciphertext)
    if type(signature) is not bytes or len(signature) != SIGNATURE_SIZE:
        raise WireError('invalid signature size or type')
    return header.encode() + ciphertext + signature


def split_untrusted(wire: bytes) -> tuple[Header, bytes, bytes]:
    """Structurally parse untrusted bytes. The returned values are not authenticated."""
    if type(wire) is not bytes or not MIN_WIRE_SIZE <= len(wire) <= MAX_WIRE_SIZE:
        raise WireError('invalid envelope size or type')
    version, account, record, sender, epoch, nonce = _HEADER.unpack_from(wire)
    if version != VERSION:
        raise WireError('unsupported version')
    header = Header(UUID(bytes=account), UUID(bytes=record), UUID(bytes=sender), epoch, nonce)
    ciphertext = wire[HEADER_SIZE:-SIGNATURE_SIZE]
    _require_ciphertext(ciphertext)
    return header, ciphertext, wire[-SIGNATURE_SIZE:]


def signature_input(header: Header, ciphertext: bytes) -> bytes:
    if type(header) is not Header:
        raise WireError('invalid header')
    _require_ciphertext(ciphertext)
    return DOMAIN + header.encode() + ciphertext
