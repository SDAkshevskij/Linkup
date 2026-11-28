package io.linkup.app.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/** E2EE v1 binary framing only. Successful parsing does not establish trust. */
public final class SecureWire {
    private SecureWire() {}
    public static final int HEADER_SIZE = 81;
    public static final int TAG_SIZE = 16;
    public static final int SIGNATURE_SIZE = 64;
    public static final int MAX_PLAINTEXT = 1 << 20;
    private static final int MIN_WIRE_SIZE = HEADER_SIZE + TAG_SIZE + SIGNATURE_SIZE;
    private static final byte[] DOMAIN = "LinkUp/record/v1\0".getBytes(StandardCharsets.US_ASCII);

    public static final class Header {
        private final UUID account, record, sender;
        private final long epoch;
        private final byte[] nonce;

        public Header(UUID account, UUID record, UUID sender, long epoch, byte[] nonce) {
            this.account = requireId(account);
            this.record = requireId(record);
            this.sender = requireId(sender);
            if (epoch <= 0) throw new IllegalArgumentException("invalid epoch");
            if (nonce == null || nonce.length != 24) throw new IllegalArgumentException("invalid nonce");
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
            b.put((byte) 1);
            putId(b, account); putId(b, record); putId(b, sender);
            return b.putLong(epoch).put(nonce).array();
        }
    }

    public static final class Parts {
        private final Header header;
        private final byte[] ciphertext, signature;
        private Parts(Header header, byte[] ciphertext, byte[] signature) {
            this.header = header;
            this.ciphertext = ciphertext;
            this.signature = signature;
        }
        public Header header() { return header; }
        public byte[] ciphertext() { return ciphertext.clone(); }
        public byte[] signature() { return signature.clone(); }
    }

    private static UUID requireId(UUID id) {
        if (id == null || (id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0))
            throw new IllegalArgumentException("invalid identifier");
        return id;
    }
    private static void putId(ByteBuffer b, UUID id) {
        b.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
    }
    private static UUID readId(ByteBuffer b) { return new UUID(b.getLong(), b.getLong()); }

    public static Parts split(byte[] wire) {
        if (wire == null || wire.length < MIN_WIRE_SIZE || wire.length > MIN_WIRE_SIZE + MAX_PLAINTEXT)
            throw new IllegalArgumentException("invalid envelope size");
        byte[] snapshot = wire.clone();
        ByteBuffer b = ByteBuffer.wrap(snapshot);
        if (b.get() != 1) throw new IllegalArgumentException("unsupported version");
        UUID account = readId(b), record = readId(b), sender = readId(b);
        long epoch = b.getLong();
        byte[] nonce = new byte[24]; b.get(nonce);
        Header header = new Header(account, record, sender, epoch, nonce);
        int end = snapshot.length - SIGNATURE_SIZE;
        return new Parts(header, Arrays.copyOfRange(snapshot, HEADER_SIZE, end),
                Arrays.copyOfRange(snapshot, end, snapshot.length));
    }

    public static byte[] signatureInput(Header header, byte[] ciphertext) {
        if (header == null || ciphertext == null || ciphertext.length < TAG_SIZE || ciphertext.length > MAX_PLAINTEXT + TAG_SIZE)
            throw new IllegalArgumentException("invalid signature input");
        return ByteBuffer.allocate(DOMAIN.length + HEADER_SIZE + ciphertext.length)
                .put(DOMAIN).put(header.encode()).put(ciphertext).array();
    }
}
