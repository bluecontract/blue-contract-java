package blue.contract.processor.conversation.javascript.chicory;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeterministicValueCodec {
    public static final int MAX_DEPTH = 64;
    public static final int MAX_ENCODED_BYTES = 5 * 1024 * 1024;
    public static final int MAX_STRING_BYTES = 256 * 1024;
    public static final int MAX_ARRAY_LENGTH = 65535;
    public static final int MAX_MAP_SIZE = 65535;
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;
    private static final long MIN_SAFE_INTEGER = -9007199254740991L;

    private DeterministicValueCodec() {
    }

    public static byte[] encode(Object value) {
        LimitedByteArrayOutputStream output = new LimitedByteArrayOutputStream(MAX_ENCODED_BYTES);
        encodeValue(value, output, 0);
        return output.toByteArray();
    }

    public static Object decode(byte[] bytes) {
        if (bytes == null) {
            throw new DeterministicValueException("input bytes must not be null");
        }
        if (bytes.length > MAX_ENCODED_BYTES) {
            throw new DeterministicValueException("encoded value exceeds max bytes: " + bytes.length);
        }
        Decoder decoder = new Decoder(bytes);
        Object value = decoder.decodeValue(0);
        if (!decoder.done()) {
            throw new DeterministicValueException("trailing bytes after DV value");
        }
        return value;
    }

    public static Object roundTrip(Object value) {
        return decode(encode(value));
    }

    private static void encodeValue(Object value, LimitedByteArrayOutputStream output, int depth) {
        if (value == null) {
            output.writeByte(0xf6);
            return;
        }
        if (value instanceof Boolean) {
            output.writeByte(Boolean.TRUE.equals(value) ? 0xf5 : 0xf4);
            return;
        }
        if (value instanceof String) {
            encodeString((String) value, output);
            return;
        }
        if (value instanceof Number) {
            encodeNumber((Number) value, output);
            return;
        }
        if (value instanceof Map) {
            encodeMap((Map<?, ?>) value, output, depth);
            return;
        }
        if (value instanceof Collection) {
            encodeArray(new ArrayList<Object>((Collection<?>) value), output, depth);
            return;
        }
        if (value instanceof Object[]) {
            encodeArray(Arrays.asList((Object[]) value), output, depth);
            return;
        }
        throw new DeterministicValueException("unsupported DV value type: " + value.getClass().getName());
    }

    private static void encodeNumber(Number number, LimitedByteArrayOutputStream output) {
        if (number instanceof Float) {
            throw new DeterministicValueException("float32 values are not supported");
        }
        if (number instanceof Double) {
            double value = number.doubleValue();
            if (!Double.isFinite(value)) {
                throw new DeterministicValueException("number must be finite");
            }
            if (Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(-0.0d)) {
                throw new DeterministicValueException("negative zero is not canonical DV");
            }
            if (isMathematicalInteger(value) && value >= MIN_SAFE_INTEGER && value <= MAX_SAFE_INTEGER) {
                encodeInteger((long) value, output);
                return;
            }
            encodeFloat64(value, output);
            return;
        }
        if (number instanceof BigDecimal) {
            BigDecimal decimal = ((BigDecimal) number).stripTrailingZeros();
            if (decimal.scale() <= 0) {
                encodeBigInteger(decimal.toBigIntegerExact(), output);
                return;
            }
            double value = decimal.doubleValue();
            if (!Double.isFinite(value)) {
                throw new DeterministicValueException("number must be finite");
            }
            if (isMathematicalInteger(value)) {
                throw new DeterministicValueException("non-integer BigDecimal lost precision as integer");
            }
            encodeFloat64(value, output);
            return;
        }
        if (number instanceof BigInteger) {
            encodeBigInteger((BigInteger) number, output);
            return;
        }
        encodeInteger(number.longValue(), output);
    }

    private static boolean isMathematicalInteger(double value) {
        return value == Math.rint(value);
    }

    private static void encodeBigInteger(BigInteger integer, LimitedByteArrayOutputStream output) {
        if (integer.compareTo(BigInteger.valueOf(MIN_SAFE_INTEGER)) < 0
                || integer.compareTo(BigInteger.valueOf(MAX_SAFE_INTEGER)) > 0) {
            throw new DeterministicValueException("integer exceeds safe integer range");
        }
        encodeInteger(integer.longValue(), output);
    }

    private static void encodeInteger(long value, LimitedByteArrayOutputStream output) {
        if (value < MIN_SAFE_INTEGER || value > MAX_SAFE_INTEGER) {
            throw new DeterministicValueException("integer exceeds safe integer range");
        }
        if (value >= 0) {
            writeTypeAndLength(0, value, output);
        } else {
            writeTypeAndLength(1, -1L - value, output);
        }
    }

    private static void encodeFloat64(double value, LimitedByteArrayOutputStream output) {
        output.writeByte(0xfb);
        long bits = Double.doubleToLongBits(value);
        for (int i = 7; i >= 0; i--) {
            output.writeByte((int) ((bits >>> (i * 8)) & 0xff));
        }
    }

    private static void encodeString(String value, LimitedByteArrayOutputStream output) {
        byte[] encoded = utf8(value);
        if (encoded.length > MAX_STRING_BYTES) {
            throw new DeterministicValueException("string exceeds max UTF-8 bytes: " + encoded.length);
        }
        writeTypeAndLength(3, encoded.length, output);
        output.writeBytes(encoded);
    }

    private static byte[] utf8(String value) {
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            ByteBuffer buffer = encoder.encode(java.nio.CharBuffer.wrap(value));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException ex) {
            throw new DeterministicValueException("string is not well-formed UTF-16/UTF-8", ex);
        }
    }

    private static void encodeArray(List<?> values, LimitedByteArrayOutputStream output, int depth) {
        int nextDepth = depth + 1;
        if (nextDepth > MAX_DEPTH) {
            throw new DeterministicValueException("maximum DV depth exceeded");
        }
        if (values.size() > MAX_ARRAY_LENGTH) {
            throw new DeterministicValueException("array exceeds max length: " + values.size());
        }
        writeTypeAndLength(4, values.size(), output);
        for (Object value : values) {
            encodeValue(value, output, nextDepth);
        }
    }

    private static void encodeMap(Map<?, ?> map, LimitedByteArrayOutputStream output, int depth) {
        int nextDepth = depth + 1;
        if (nextDepth > MAX_DEPTH) {
            throw new DeterministicValueException("maximum DV depth exceeded");
        }
        if (map.size() > MAX_MAP_SIZE) {
            throw new DeterministicValueException("map exceeds max size: " + map.size());
        }
        List<MapEntry> entries = new ArrayList<MapEntry>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw new DeterministicValueException("map keys must be strings");
            }
            String key = (String) entry.getKey();
            entries.add(new MapEntry(key, utf8(key), entry.getValue()));
        }
        Collections.sort(entries, new Comparator<MapEntry>() {
            @Override
            public int compare(MapEntry left, MapEntry right) {
                return compareEncodedKeys(left.encodedKey, right.encodedKey);
            }
        });
        writeTypeAndLength(5, entries.size(), output);
        for (MapEntry entry : entries) {
            encodeString(entry.key, output);
            encodeValue(entry.value, output, nextDepth);
        }
    }

    private static int compareEncodedKeys(byte[] leftUtf8, byte[] rightUtf8) {
        byte[] left = encodedTextKey(leftUtf8);
        byte[] right = encodedTextKey(rightUtf8);
        if (left.length != right.length) {
            return left.length < right.length ? -1 : 1;
        }
        for (int i = 0; i < left.length; i++) {
            int a = left[i] & 0xff;
            int b = right[i] & 0xff;
            if (a != b) {
                return a < b ? -1 : 1;
            }
        }
        return 0;
    }

    private static byte[] encodedTextKey(byte[] utf8) {
        LimitedByteArrayOutputStream output = new LimitedByteArrayOutputStream(MAX_STRING_BYTES + 16);
        writeTypeAndLength(3, utf8.length, output);
        output.writeBytes(utf8);
        return output.toByteArray();
    }

    private static void writeTypeAndLength(int majorType, long value, LimitedByteArrayOutputStream output) {
        int major = majorType << 5;
        if (value < 0) {
            throw new DeterministicValueException("negative CBOR length");
        }
        if (value <= 23) {
            output.writeByte(major | (int) value);
        } else if (value <= 0xffL) {
            output.writeByte(major | 24);
            output.writeByte((int) value);
        } else if (value <= 0xffffL) {
            output.writeByte(major | 25);
            output.writeByte((int) ((value >>> 8) & 0xff));
            output.writeByte((int) (value & 0xff));
        } else if (value <= 0xffffffffL) {
            output.writeByte(major | 26);
            for (int i = 3; i >= 0; i--) {
                output.writeByte((int) ((value >>> (i * 8)) & 0xff));
            }
        } else {
            output.writeByte(major | 27);
            for (int i = 7; i >= 0; i--) {
                output.writeByte((int) ((value >>> (i * 8)) & 0xff));
            }
        }
    }

    private static Number decodeInteger(long value) {
        if (value < MIN_SAFE_INTEGER || value > MAX_SAFE_INTEGER) {
            throw new DeterministicValueException("integer exceeds safe integer range");
        }
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return Integer.valueOf((int) value);
        }
        return Long.valueOf(value);
    }

    private static final class MapEntry {
        private final String key;
        private final byte[] encodedKey;
        private final Object value;

        private MapEntry(String key, byte[] encodedKey, Object value) {
            this.key = key;
            this.encodedKey = encodedKey;
            this.value = value;
        }
    }

    private static final class LimitedByteArrayOutputStream {
        private final int limit;
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();

        private LimitedByteArrayOutputStream(int limit) {
            this.limit = limit;
        }

        private void writeByte(int value) {
            if (delegate.size() + 1 > limit) {
                throw new DeterministicValueException("encoded value exceeds max bytes");
            }
            delegate.write(value & 0xff);
        }

        private void writeBytes(byte[] bytes) {
            if (delegate.size() + bytes.length > limit) {
                throw new DeterministicValueException("encoded value exceeds max bytes");
            }
            delegate.write(bytes, 0, bytes.length);
        }

        private byte[] toByteArray() {
            return delegate.toByteArray();
        }
    }

    private static final class Decoder {
        private final byte[] bytes;
        private int offset;

        private Decoder(byte[] bytes) {
            this.bytes = bytes;
        }

        private boolean done() {
            return offset == bytes.length;
        }

        private Object decodeValue(int depth) {
            if (offset >= bytes.length) {
                throw new DeterministicValueException("unexpected end of DV value");
            }
            int initial = readU8();
            int major = (initial >>> 5) & 0x07;
            int additional = initial & 0x1f;
            switch (major) {
                case 0:
                    return decodeInteger(readArgument(additional));
                case 1: {
                    long encoded = readArgument(additional);
                    if (encoded >= MAX_SAFE_INTEGER) {
                        throw new DeterministicValueException("negative integer exceeds safe integer range");
                    }
                    return decodeInteger(-1L - encoded);
                }
                case 2:
                    throw new DeterministicValueException("byte strings are not valid DV");
                case 3:
                    return decodeString(additional);
                case 4:
                    return decodeArray(additional, depth);
                case 5:
                    return decodeMap(additional, depth);
                case 6:
                    throw new DeterministicValueException("CBOR tags are not valid DV");
                case 7:
                    return decodeSimple(additional);
                default:
                    throw new DeterministicValueException("unsupported DV major type");
            }
        }

        private Object decodeSimple(int additional) {
            if (additional == 20) {
                return Boolean.FALSE;
            }
            if (additional == 21) {
                return Boolean.TRUE;
            }
            if (additional == 22) {
                return null;
            }
            if (additional == 25) {
                throw new DeterministicValueException("float16 is not valid DV");
            }
            if (additional == 26) {
                throw new DeterministicValueException("float32 is not valid DV");
            }
            if (additional == 27) {
                double value = Double.longBitsToDouble(readUint64Bits());
                if (!Double.isFinite(value)) {
                    throw new DeterministicValueException("number must be finite");
                }
                if (Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(-0.0d)) {
                    throw new DeterministicValueException("negative zero is not canonical DV");
                }
                if (isMathematicalInteger(value)) {
                    throw new DeterministicValueException("integer-valued float64 is not canonical DV");
                }
                return Double.valueOf(value);
            }
            if (additional == 31) {
                throw new DeterministicValueException("indefinite-length values are not valid DV");
            }
            throw new DeterministicValueException("unsupported CBOR simple value");
        }

        private String decodeString(int additional) {
            long length = readArgument(additional);
            if (length > MAX_STRING_BYTES) {
                throw new DeterministicValueException("string exceeds max UTF-8 bytes: " + length);
            }
            if (length > bytes.length - offset) {
                throw new DeterministicValueException("string length exceeds remaining input");
            }
            byte[] encoded = Arrays.copyOfRange(bytes, offset, offset + (int) length);
            offset += (int) length;
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(encoded))
                        .toString();
            } catch (CharacterCodingException ex) {
                throw new DeterministicValueException("string payload is not valid UTF-8", ex);
            }
        }

        private List<Object> decodeArray(int additional, int depth) {
            int nextDepth = depth + 1;
            if (nextDepth > MAX_DEPTH) {
                throw new DeterministicValueException("maximum DV depth exceeded");
            }
            long length = readArgument(additional);
            if (length > MAX_ARRAY_LENGTH) {
                throw new DeterministicValueException("array exceeds max length: " + length);
            }
            List<Object> values = new ArrayList<Object>((int) length);
            for (int i = 0; i < length; i++) {
                values.add(decodeValue(nextDepth));
            }
            return values;
        }

        private Map<String, Object> decodeMap(int additional, int depth) {
            int nextDepth = depth + 1;
            if (nextDepth > MAX_DEPTH) {
                throw new DeterministicValueException("maximum DV depth exceeded");
            }
            long length = readArgument(additional);
            if (length > MAX_MAP_SIZE) {
                throw new DeterministicValueException("map exceeds max size: " + length);
            }
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            byte[] previous = null;
            for (int i = 0; i < length; i++) {
                int keyStart = offset;
                Object key = decodeValue(nextDepth);
                if (!(key instanceof String)) {
                    throw new DeterministicValueException("map keys must be strings");
                }
                byte[] encodedKey = Arrays.copyOfRange(bytes, keyStart, offset);
                if (previous != null && compareCanonicalKeys(previous, encodedKey) >= 0) {
                    throw new DeterministicValueException("map keys must be unique and sorted canonically");
                }
                previous = encodedKey;
                Object value = decodeValue(nextDepth);
                map.put((String) key, value);
            }
            return map;
        }

        private int compareCanonicalKeys(byte[] left, byte[] right) {
            if (left.length != right.length) {
                return left.length < right.length ? -1 : 1;
            }
            for (int i = 0; i < left.length; i++) {
                int a = left[i] & 0xff;
                int b = right[i] & 0xff;
                if (a != b) {
                    return a < b ? -1 : 1;
                }
            }
            return 0;
        }

        private long readArgument(int additional) {
            if (additional < 24) {
                return additional;
            }
            if (additional == 24) {
                int value = readU8();
                if (value < 24) {
                    throw new DeterministicValueException("non-canonical integer/length width");
                }
                return value;
            }
            if (additional == 25) {
                long value = readUnsigned(2);
                if (value <= 0xffL) {
                    throw new DeterministicValueException("non-canonical integer/length width");
                }
                return value;
            }
            if (additional == 26) {
                long value = readUnsigned(4);
                if (value <= 0xffffL) {
                    throw new DeterministicValueException("non-canonical integer/length width");
                }
                return value;
            }
            if (additional == 27) {
                long value = readUnsigned(8);
                if (value <= 0xffffffffL) {
                    throw new DeterministicValueException("non-canonical integer/length width");
                }
                return value;
            }
            if (additional == 31) {
                throw new DeterministicValueException("indefinite-length values are not valid DV");
            }
            throw new DeterministicValueException("unsupported CBOR additional information");
        }

        private long readUnsigned(int count) {
            if (count > bytes.length - offset) {
                throw new DeterministicValueException("unexpected end of DV value");
            }
            long value = 0L;
            for (int i = 0; i < count; i++) {
                value = (value << 8) | readU8();
            }
            if (count == 8 && value < 0) {
                throw new DeterministicValueException("uint64 value exceeds Java signed range");
            }
            return value;
        }

        private long readUint64Bits() {
            if (8 > bytes.length - offset) {
                throw new DeterministicValueException("unexpected end of DV float64");
            }
            long value = 0L;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | readU8();
            }
            return value;
        }

        private int readU8() {
            if (offset >= bytes.length) {
                throw new DeterministicValueException("unexpected end of DV value");
            }
            return bytes[offset++] & 0xff;
        }
    }

    public static final class DeterministicValueException extends BlueQuickJsDeterminismException {
        public DeterministicValueException(String message) {
            super(message);
        }

        public DeterministicValueException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
