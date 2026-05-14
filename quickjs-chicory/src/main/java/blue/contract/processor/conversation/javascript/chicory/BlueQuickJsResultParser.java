package blue.contract.processor.conversation.javascript.chicory;

import java.math.BigInteger;

public final class BlueQuickJsResultParser {
    private static final String RESULT_PREFIX = "RESULT";
    private static final String ERROR_PREFIX = "ERROR";
    private static final String GAS_MARKER = " GAS remaining=";
    private static final String USED_MARKER = " used=";

    private BlueQuickJsResultParser() {
    }

    public static ParsedResult parse(String raw) {
        if (raw == null) {
            throw new BlueQuickJsDeterminismException("VM output must not be null");
        }
        String normalized = raw.trim();
        boolean ok;
        String withoutKind;
        if (normalized.startsWith(RESULT_PREFIX)) {
            ok = true;
            withoutKind = normalized.substring(RESULT_PREFIX.length()).trim();
        } else if (normalized.startsWith(ERROR_PREFIX)) {
            ok = false;
            withoutKind = normalized.substring(ERROR_PREFIX.length()).trim();
        } else {
            throw new BlueQuickJsDeterminismException("Unexpected VM output prefix: " + normalized);
        }

        int gasIndex = withoutKind.lastIndexOf(GAS_MARKER);
        if (gasIndex < 0) {
            throw new BlueQuickJsDeterminismException("Missing gas trailer in VM output: " + normalized);
        }
        String payload = withoutKind.substring(0, gasIndex).trim();
        String trailer = withoutKind.substring(gasIndex + GAS_MARKER.length());
        int usedIndex = trailer.lastIndexOf(USED_MARKER);
        if (usedIndex < 0) {
            throw new BlueQuickJsDeterminismException("Missing used gas trailer in VM output: " + normalized);
        }
        long gasRemaining = parseGas(trailer.substring(0, usedIndex), "gasRemaining");
        long gasUsed = parseGas(trailer.substring(usedIndex + USED_MARKER.length()), "wasmGasUsed");

        if (ok) {
            return ParsedResult.success(DeterministicValueCodec.decode(hexToBytes(payload)), gasRemaining, gasUsed, raw);
        }
        return ParsedResult.error(payload, gasRemaining, gasUsed, raw);
    }

    private static long parseGas(String value, String field) {
        String trimmed = value.trim();
        try {
            BigInteger parsed = new BigInteger(trimmed);
            if (parsed.signum() < 0 || parsed.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                throw new BlueQuickJsDeterminismException(field + " is outside supported range: " + trimmed);
            }
            return parsed.longValue();
        } catch (NumberFormatException ex) {
            throw new BlueQuickJsDeterminismException("Invalid " + field + ": " + trimmed, ex);
        }
    }

    private static byte[] hexToBytes(String hex) {
        if ((hex.length() & 1) != 0) {
            throw new BlueQuickJsDeterminismException("result payload hex must have even length");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new BlueQuickJsDeterminismException("result payload contains non-hex characters");
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    public static final class ParsedResult {
        private final boolean ok;
        private final Object value;
        private final String errorMessage;
        private final long gasRemaining;
        private final long wasmGasUsed;
        private final String raw;

        private ParsedResult(boolean ok,
                             Object value,
                             String errorMessage,
                             long gasRemaining,
                             long wasmGasUsed,
                             String raw) {
            this.ok = ok;
            this.value = value;
            this.errorMessage = errorMessage;
            this.gasRemaining = gasRemaining;
            this.wasmGasUsed = wasmGasUsed;
            this.raw = raw;
        }

        private static ParsedResult success(Object value, long gasRemaining, long wasmGasUsed, String raw) {
            return new ParsedResult(true, value, null, gasRemaining, wasmGasUsed, raw);
        }

        private static ParsedResult error(String errorMessage, long gasRemaining, long wasmGasUsed, String raw) {
            return new ParsedResult(false, null, errorMessage, gasRemaining, wasmGasUsed, raw);
        }

        public boolean ok() {
            return ok;
        }

        public Object value() {
            return value;
        }

        public String errorMessage() {
            return errorMessage;
        }

        public long gasRemaining() {
            return gasRemaining;
        }

        public long wasmGasUsed() {
            return wasmGasUsed;
        }

        public String raw() {
            return raw;
        }
    }
}
