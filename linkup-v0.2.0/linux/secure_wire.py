"""Canonical E2EE v1 record framing. Parsing alone never authenticates a record."""
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
MIN_WIRE_SIZE = HEADER_SIZE + TAG_SIZE + SIGNATURE_SIZE
MAX_WIRE_SIZE = MIN_WIRE_SIZE + MAX_PLAINTEXT


class WireError(ValueError):
    """Malformed or unsupported record framing; never a reason to downgrade."""


@dataclass(frozen=True)
class Header:
    account: UUID
    record: UUID
    sender: UUID
    epoch: int
    nonce: bytes

    def __post_init__(self):
        if any(not isinstance(value, UUID) or value.int == 0
               for value in (self.account, self.record, self.sender)):
            raise WireError('invalid identifier')
        if type(self.epoch) is not int or not 1 <= self.epoch < 2**63:
            raise WireError('invalid epoch')
        if type(self.nonce) is not bytes or len(self.nonce) != NONCE_SIZE:
            raise WireError('invalid nonce')

    def encode(self) -> bytes:
        return _HEADER.pack(VERSION, self.account.bytes, self.record.bytes,
                            self.sender.bytes, self.epoch, self.nonce)


def split(wire: bytes) -> tuple[Header, bytes, bytes]:
    """Return untrusted header/ciphertext/signature after structural validation."""
    if type(wire) is not bytes or not MIN_WIRE_SIZE <= len(wire) <= MAX_WIRE_SIZE:
        raise WireError('invalid envelope size or type')
    version, account, record, sender, epoch, nonce = _HEADER.unpack_from(wire)
    if version != VERSION:
        raise WireError('unsupported version')
    header = Header(UUID(bytes=account), UUID(bytes=record), UUID(bytes=sender), epoch, nonce)
    return header, wire[HEADER_SIZE:-SIGNATURE_SIZE], wire[-SIGNATURE_SIZE:]


def signature_input(header: Header, ciphertext: bytes) -> bytes:
    if not isinstance(header, Header):
        raise WireError('invalid header')
    if type(ciphertext) is not bytes or not TAG_SIZE <= len(ciphertext) <= MAX_PLAINTEXT + TAG_SIZE:
        raise WireError('invalid ciphertext size or type')
    return DOMAIN + header.encode() + ciphertext
