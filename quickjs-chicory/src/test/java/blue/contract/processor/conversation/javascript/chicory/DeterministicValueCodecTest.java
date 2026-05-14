package blue.contract.processor.conversation.javascript.chicory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeterministicValueCodecTest {

    @Test
    void allowedValuesRoundTrip() {
        assertRoundTrip(null, null);
        assertRoundTrip(Boolean.TRUE, Boolean.TRUE);
        assertRoundTrip(Boolean.FALSE, Boolean.FALSE);
        assertRoundTrip(0, 0);
        assertRoundTrip(1, 1);
        assertRoundTrip(-1, -1);
        assertRoundTrip(BigInteger.valueOf(9007199254740991L), 9007199254740991L);
        assertRoundTrip(BigInteger.valueOf(-9007199254740991L), -9007199254740991L);
        assertRoundTrip(1.5d, 1.5d);
        assertRoundTrip("hello", "hello");
        assertRoundTrip(Collections.emptyList(), Collections.emptyList());
        assertRoundTrip(Arrays.asList(1, Boolean.TRUE, null), Arrays.asList(1, Boolean.TRUE, null));

        Map<String, Object> ok = new LinkedHashMap<String, Object>();
        ok.put("ok", Boolean.TRUE);
        assertRoundTrip(ok, ok);

        Map<String, Object> canonicalOrder = new LinkedHashMap<String, Object>();
        canonicalOrder.put("b", 2);
        canonicalOrder.put("aa", 1);
        assertRoundTrip(canonicalOrder, canonicalOrder);
    }

    @Test
    void goldenEncodingsMatchBlueQuickJsDocs() {
        assertHex("f6", null);
        assertHex("f5", Boolean.TRUE);
        assertHex("20", -1);
        assertHex("826568656c6c6ffb3ff8000000000000", Arrays.asList("hello", 1.5d));

        Map<String, Object> ok = new LinkedHashMap<String, Object>();
        ok.put("ok", Boolean.TRUE);
        assertHex("a1626f6bf5", ok);

        Map<String, Object> ordered = new LinkedHashMap<String, Object>();
        ordered.put("b", 2);
        ordered.put("aa", 1);
        assertHex("a261620262616101", ordered);
    }

    @Test
    void nestedObjectAtMaxDepthIsAllowed() {
        Object value = "leaf";
        for (int i = 0; i < DeterministicValueCodec.MAX_DEPTH; i++) {
            value = Collections.singletonList(value);
        }

        Object decoded = DeterministicValueCodec.decode(DeterministicValueCodec.encode(value));

        assertEquals(value, decoded);
    }

    @Test
    void forbiddenNumbersAreRejected() {
        assertEncodeFails(Double.NaN, "finite");
        assertEncodeFails(Double.POSITIVE_INFINITY, "finite");
        assertEncodeFails(Double.NEGATIVE_INFINITY, "finite");
        assertEncodeFails(-0.0d, "negative zero");
        assertEncodeFails(Float.valueOf(1.5f), "float32");
    }

    @Test
    void forbiddenCborFormsAreRejected() {
        assertDecodeFails("a2616101616102", "unique and sorted");
        assertDecodeFails("a262616101616202", "unique and sorted");
        assertDecodeFails("9fff", "indefinite");
        assertDecodeFails("bfff", "indefinite");
        assertDecodeFails("7fff", "indefinite");
        assertDecodeFails("c0f6", "tags");
        assertDecodeFails("f93c00", "float16");
        assertDecodeFails("fa3f800000", "float32");
        assertDecodeFails("fb7ff8000000000000", "finite");
        assertDecodeFails("fb8000000000000000", "negative zero");
        assertDecodeFails("fb3ff0000000000000", "integer-valued");
        assertDecodeFails("1817", "non-canonical");
    }

    @Test
    void limitsAreEnforced() {
        assertEncodeFails(repeat('x', DeterministicValueCodec.MAX_STRING_BYTES + 1), "string exceeds");

        Object tooDeep = "leaf";
        for (int i = 0; i < DeterministicValueCodec.MAX_DEPTH + 1; i++) {
            tooDeep = Collections.singletonList(tooDeep);
        }
        assertEncodeFails(tooDeep, "depth");

        List<Object> tooLongArray = new ArrayList<Object>();
        for (int i = 0; i < DeterministicValueCodec.MAX_ARRAY_LENGTH + 1; i++) {
            tooLongArray.add(null);
        }
        assertEncodeFails(tooLongArray, "array exceeds");

        Map<String, Object> tooLargeMap = new LinkedHashMap<String, Object>();
        for (int i = 0; i < DeterministicValueCodec.MAX_MAP_SIZE + 1; i++) {
            tooLargeMap.put("k" + i, i);
        }
        assertEncodeFails(tooLargeMap, "map exceeds");

        List<Object> tooManyBytes = new ArrayList<Object>();
        String eighty = repeat('y', 80);
        for (int i = 0; i < DeterministicValueCodec.MAX_ARRAY_LENGTH; i++) {
            tooManyBytes.add(eighty);
        }
        assertEncodeFails(tooManyBytes, "encoded value exceeds");
    }

    @Test
    void decodeLimitsAreEnforced() {
        assertDecodeFails("7a00040001", "string exceeds");
        assertDecodeFails("9a00010000", "array exceeds");
        assertDecodeFails("ba00010000", "map exceeds");
    }

    private static void assertRoundTrip(Object value, Object expected) {
        assertEquals(expected, DeterministicValueCodec.roundTrip(value));
    }

    private static void assertHex(String expectedHex, Object value) {
        assertArrayEquals(bytes(expectedHex), DeterministicValueCodec.encode(value));
        assertEquals(DeterministicValueCodec.decode(bytes(expectedHex)), DeterministicValueCodec.roundTrip(value));
    }

    private static void assertEncodeFails(Object value, String messagePart) {
        DeterministicValueCodec.DeterministicValueException ex = assertThrows(
                DeterministicValueCodec.DeterministicValueException.class,
                () -> DeterministicValueCodec.encode(value));
        assertContains(ex, messagePart);
    }

    private static void assertDecodeFails(String hex, String messagePart) {
        DeterministicValueCodec.DeterministicValueException ex = assertThrows(
                DeterministicValueCodec.DeterministicValueException.class,
                () -> DeterministicValueCodec.decode(bytes(hex)));
        assertContains(ex, messagePart);
    }

    private static void assertContains(Exception ex, String messagePart) {
        String message = ex.getMessage();
        if (message == null || !message.contains(messagePart)) {
            throw new AssertionError("Expected message to contain \"" + messagePart + "\" but was: " + message);
        }
    }

    private static byte[] bytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
