package io.linkup.app.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * Canonical structural framing for LinkUp E2EE v1.
 * Parsing is deliberately named untrusted: trust/signature verification must happen before decryption or side effects.
 */
public final class SecureWire {
    private SecureWire() {}

    public static final int VERSION = 1;
    public static final int HEADER_SIZE = 81;
    public static final int TAG_SIZE = 16;
    public static final int SIGNATURE_SIZE = 64;
    public static final int MAX_PLAINTEXT = 1 << 20;
    public static final int MIN_CIPHERTEXT_SIZE = TAG_SIZE;
    public static final int MAX_CIPHERTEXT_SIZE = MAX_PLAINTEXT + TAG_SIZE;
    public static final int MIN_WIRE_SIZE = HEADER_SIZE + MIN_CIPHERTEXT_SIZE + SIGNATURE_SIZE;
    public static final int MAX_WIRE_SIZE = HEADER_SIZE + MAX_CIPHERTEXT_SIZE + SIGNATURE_SIZE;
    private static final int NONCE_SIZE = 24;
    private static final byte[] DOMAIN = "LinkUp/record/v1\0".getBytes(StandardCharsets.US_ASCII);

    public static final class Header {
        private final UUID account;
        private final UUID record;
        private final UUID sender;
        private final long epoch;
        private final byte[] nonce;

        public Header(UUID account, UUID record, UUID sender, long epoch, byte[] nonce) {
            this.account = requireId(account);
            this.record = requireId(record);
            this.sender = requireId(sender);
            if (epoch <= 0) throw new IllegalArgumentException("invalid epoch");
            if (nonce == null || nonce.length != NONCE_SIZE) throw new IllegalArgumentException("invalid nonce");
            this.epoch = epoch;
            this.nonce = nonce.clone();
        }

        public UUID account() { return account; }
        public UUID record() { return record; }
        public UUID sender() { return sender; }
        public long epoch() { return epoch; }
        public byte[] nonce() { return nonce.clone(); }

        public byte[] encode() {
            ByteBuffer b = ByteBuffer.allocate(HEADER_SIZE);
            b.put((byte) VERSION);
            putId(b, account);
            putId(b, record);
            putId(b, sender);
            return b.putLong(epoch).put(nonce).array();
        }
    }

    public static final class UntrustedParts {
        private final Header header;
        private final byte[] ciphertext;
        private final byte[] signature;

        private UntrustedParts(Header header, byte[] ciphertext, byte[] signature) {
            this.header = header;
            this.ciphertext = ciphertext;
            this.signature = signature;
        }

        public Header header() { return header; }
        public byte[] ciphertext() { return ciphertext.clone(); }
        public byte[] signature() { return signature.clone(); }
    }

    private static UUID requireId(UUID id) {
        if (id == null || (id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0)) {
            throw new IllegalArgumentException("invalid identifier");
        }
        return id;
    }

    private static void putId(ByteBuffer b, UUID id) {
        b.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
    }

    private static UUID readId(ByteBuffer b) {
        return new UUID(b.getLong(), b.getLong());
    }

    private static void requireCiphertext(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length < MIN_CIPHERTEXT_SIZE || ciphertext.length > MAX_CIPHERTEXT_SIZE) {
            throw new IllegalArgumentException("invalid ciphertext size");
        }
    }

    public static byte[] assemble(Header header, byte[] ciphertext, byte[] signature) {
        if (header == null) throw new IllegalArgumentException("invalid header");
        requireCiphertext(ciphertext);
        if (signature == null || signature.length != SIGNATURE_SIZE) {
            throw new IllegalArgumentException("invalid signature size");
        }
        return ByteBuffer.allocate(HEADER_SIZE + ciphertext.length + SIGNATURE_SIZE)
                .put(header.encode()).put(ciphertext).put(signature).array();
    }

    public static UntrustedParts splitUntrusted(byte[] wire) {
        if (wire == null || wire.length < MIN_WIRE_SIZE || wire.length > MAX_WIRE_SIZE) {
            throw new IllegalArgumentException("invalid envelope size");
        }
        byte[] snapshot = wire.clone();
        ByteBuffer b = ByteBuffer.wrap(snapshot);
        if (Byte.toUnsignedInt(b.get()) != VERSION) throw new IllegalArgumentException("unsupported version");
        UUID account = readId(b);
        UUID record = readId(b);
        UUID sender = readId(b);
        long epoch = b.getLong();
        byte[] nonce = new byte[NONCE_SIZE];
        b.get(nonce);
        Header header = new Header(account, record, sender, epoch, nonce);
        int end = snapshot.length - SIGNATURE_SIZE;
        byte[] ciphertext = Arrays.copyOfRange(snapshot, HEADER_SIZE, end);
        requireCiphertext(ciphertext);
        return new UntrustedParts(header, ciphertext, Arrays.copyOfRange(snapshot, end, snapshot.length));
    }

    public static byte[] signatureInput(Header header, byte[] ciphertext) {
        if (header == null) throw new IllegalArgumentException("invalid header");
        requireCiphertext(ciphertext);
        return ByteBuffer.allocate(DOMAIN.length + HEADER_SIZE + ciphertext.length)
                .put(DOMAIN).put(header.encode()).put(ciphertext).array();
    }
}
