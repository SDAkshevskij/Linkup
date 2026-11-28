import io.linkup.app.security.SecureWire;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;
import java.util.UUID;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** JVM-only test of Android wire codec; no Android runtime or crypto dependency. */
public class SecureWireTest {
    private static final UUID A = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    private static final UUID R = UUID.fromString("10213243-5465-7687-98a9-bacbdcedfe0f");
    private static final UUID D = UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100");
    private static final byte[] N = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f1011121314151617");
    private static final byte[] HEADER = HexFormat.of().parseHex("0100112233445566778899aabbccddeeff102132435465768798a9bacbdcedfe0fffeeddccbbaa998877665544332211000000000000000007000102030405060708090a0b0c0d0e0f1011121314151617");
    private static void equal(byte[] a, byte[] b) { if (!Arrays.equals(a, b)) throw new AssertionError("bytes differ"); }
    private static void reject(Runnable op) {
        try { op.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("invalid input accepted");
    }
    private static byte[] join(byte[]... parts) {
        int size = 0;
        for (byte[] part : parts) size += part.length;
        ByteBuffer b = ByteBuffer.allocate(size);
        for (byte[] part : parts) b.put(part);
        return b.array();
    }
    public static void main(String[] args) throws Exception {
        byte[] cipher = new byte[16], signature = new byte[64];
        Arrays.fill(signature, (byte) 7);
        SecureWire.Header h = new SecureWire.Header(A, R, D, 7, N);
        equal(h.encode(), HEADER);
        byte[] wire = join(HEADER, cipher, signature);
        SecureWire.Parts p = SecureWire.split(wire);
        equal(p.header().encode(), HEADER); equal(p.ciphertext(), cipher); equal(p.signature(), signature);
        equal(SecureWire.signatureInput(h, cipher), join("LinkUp/record/v1\0".getBytes(StandardCharsets.US_ASCII), HEADER, cipher));
        for (int i = 0; i < wire.length; i++) { byte[] shortWire = Arrays.copyOf(wire, i); reject(() -> SecureWire.split(shortWire)); }
        for (int offset : new int[]{1,17,33,49}) {
            byte[] bad = wire.clone(); Arrays.fill(bad, offset, offset + (offset == 49 ? 8 : 16), (byte) 0);
            reject(() -> SecureWire.split(bad));
        }
        for (int version : new int[]{0,2,255}) { byte[] bad = wire.clone(); bad[0] = (byte) version; reject(() -> SecureWire.split(bad)); }
        byte[] badEpoch = wire.clone(); badEpoch[49] = (byte) 128; reject(() -> SecureWire.split(badEpoch));
        reject(() -> SecureWire.split(null));
        reject(() -> SecureWire.split(new byte[(1 << 20) + 162]));
        reject(() -> SecureWire.signatureInput(h, new byte[15]));
        byte[] external = N.clone(); SecureWire.Header immutable = new SecureWire.Header(A,R,D,7,external);
        external[0] = 100; immutable.nonce()[1] = 100; equal(immutable.encode(), HEADER);
        p.ciphertext()[0] = 100; p.signature()[0] = 100; equal(p.ciphertext(), cipher); equal(p.signature(), signature);
        Random rng = new Random(2731);
        for (int i = 0; i < 400; i++) {
            byte[] nonce = new byte[24], c = new byte[16 + rng.nextInt(512)], s = new byte[64];
            rng.nextBytes(nonce); rng.nextBytes(c); rng.nextBytes(s);
            long epoch = i % 2 == 0 ? 1 : Long.MAX_VALUE;
            byte[] header = join(Arrays.copyOf(HEADER, 49), ByteBuffer.allocate(8).putLong(epoch).array(), nonce);
            SecureWire.Parts parsed = SecureWire.split(join(header,c,s));
            equal(parsed.header().encode(), header); equal(parsed.ciphertext(), c); equal(parsed.signature(), s);
        }
        byte[] max = new byte[(1 << 20) + 16]; equal(SecureWire.split(join(HEADER,max,signature)).ciphertext(),max);
        Properties fixture = new Properties();
        try (var stream = Files.newInputStream(Path.of(args.length == 1 ? args[0] : "tests/secure_wire_v1.properties"))) {
            fixture.load(stream);
        }
        HexFormat hex = HexFormat.of();
        SecureWire.Parts known = SecureWire.split(hex.parseHex(fixture.getProperty("wire")));
        equal(known.header().encode(), hex.parseHex(fixture.getProperty("header")));
        equal(known.ciphertext(), hex.parseHex(fixture.getProperty("ciphertext")));
        equal(known.signature(), hex.parseHex(fixture.getProperty("signature")));
        equal(SecureWire.signatureInput(known.header(), known.ciphertext()), hex.parseHex(fixture.getProperty("signature_input")));
        System.out.println("SecureWire JVM: literal, malformed, immutability, bounds and 400 generated cases passed");
    }
}
