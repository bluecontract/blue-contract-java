package blue.contract.processor.conversation.javascript;

public final class QuickJsGas {
    public static final long WASM_FUEL_PER_HOST_GAS_UNIT = 1700L;
    public static final long DEFAULT_EXPRESSION_HOST_GAS_LIMIT = 40000L;
    public static final long DEFAULT_CODE_HOST_GAS_LIMIT = 100000L;

    private QuickJsGas() {
    }

    public static long toWasmFuel(long hostGas) {
        if (hostGas < 0) {
            throw new IllegalArgumentException("hostGas must not be negative");
        }
        return Math.multiplyExact(hostGas, WASM_FUEL_PER_HOST_GAS_UNIT);
    }

    public static long toHostGasUsed(long wasmFuelUsed) {
        if (wasmFuelUsed < 0) {
            throw new IllegalArgumentException("wasmFuelUsed must not be negative");
        }
        if (wasmFuelUsed == 0L) {
            return 0L;
        }
        return ((wasmFuelUsed - 1L) / WASM_FUEL_PER_HOST_GAS_UNIT) + 1L;
    }
}
