package blue.contract.processor.conversation.javascript.chicory;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

public final class BlueQuickJsWasmInstance implements AutoCloseable {
    private final BlueQuickJsWasmResources resources;
    private final BlueQuickJsHostDispatcher dispatcher;
    private final Instance instance;
    private final Memory memory;
    private final ExportFunction malloc;
    private final ExportFunction free;
    private final ExportFunction qjsDetInit;
    private final ExportFunction qjsDetEval;
    private final ExportFunction qjsDetFree;
    private boolean closed;

    public BlueQuickJsWasmInstance(BlueQuickJsWasmResources resources,
                                   BlueQuickJsHostDispatcher dispatcher) {
        if (resources == null) {
            throw new IllegalArgumentException("resources must not be null");
        }
        if (dispatcher == null) {
            throw new IllegalArgumentException("dispatcher must not be null");
        }
        this.resources = resources;
        this.dispatcher = dispatcher;
        WasmModule module = Parser.parse(resources.wasmBytes());
        this.instance = Instance.builder(module)
                .withImportValues(importValues())
                .build();
        this.memory = instance.memory();
        this.malloc = instance.export("malloc");
        this.free = instance.export("free");
        this.qjsDetInit = instance.export("qjs_det_init");
        this.qjsDetEval = instance.export("qjs_det_eval");
        this.qjsDetFree = instance.export("qjs_det_free");
    }

    public void initialize(byte[] manifestBytes,
                           String manifestHash,
                           byte[] contextBlob,
                           long gasLimit) {
        ensureOpen();
        int manifestPtr = writeBytes(manifestBytes);
        int hashPtr = writeCString(manifestHash);
        int contextPtr = contextBlob.length == 0 ? 0 : writeBytes(contextBlob);
        try {
            long[] result = qjsDetInit.apply(
                    manifestPtr,
                    manifestBytes.length,
                    hashPtr,
                    contextPtr,
                    contextBlob.length,
                    gasLimit);
            int errorPtr = (int) result[0];
            if (errorPtr != 0) {
                String error = readAndFreeCString(errorPtr);
                throw new BlueQuickJsDeterminismException("VM init failed: " + error);
            }
        } finally {
            free(manifestPtr);
            free(hashPtr);
            if (contextPtr != 0) {
                free(contextPtr);
            }
        }
    }

    public String eval(String code) {
        ensureOpen();
        int codePtr = writeCString(code);
        try {
            long[] result = qjsDetEval.apply(codePtr);
            int resultPtr = (int) result[0];
            if (resultPtr == 0) {
                throw new BlueQuickJsDeterminismException("qjs_det_eval returned a null pointer");
            }
            return readAndFreeCString(resultPtr);
        } finally {
            free(codePtr);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            qjsDetFree.apply();
            closed = true;
        }
    }

    private ImportValues importValues() {
        return ImportValues.builder()
                .addFunction(new HostFunction("host",
                        "host_call",
                        FunctionType.of(
                                Arrays.asList(ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32),
                                Collections.singletonList(ValType.I32)),
                        (hostInstance, args) -> new long[]{hostCall(hostInstance, args)}))
                .addFunction(new HostFunction("env",
                        "abort",
                        FunctionType.of(Collections.<ValType>emptyList(), Collections.<ValType>emptyList()),
                        (hostInstance, args) -> new long[0]))
                .addFunction(new HostFunction("env",
                        "__assert_fail",
                        FunctionType.of(
                                Arrays.asList(ValType.I32, ValType.I32, ValType.I32, ValType.I32),
                                Collections.<ValType>emptyList()),
                        (hostInstance, args) -> new long[0]))
                .addFunction(new HostFunction("env",
                        "emscripten_date_now",
                        FunctionType.of(Collections.<ValType>emptyList(), Collections.singletonList(ValType.F64)),
                        (hostInstance, args) -> new long[]{Double.doubleToRawLongBits(0.0d)}))
                .addFunction(new HostFunction("env",
                        "emscripten_resize_heap",
                        FunctionType.of(Collections.singletonList(ValType.I32), Collections.singletonList(ValType.I32)),
                        (hostInstance, args) -> new long[]{0L}))
                .build();
    }

    private long hostCall(Instance hostInstance, long[] args) {
        try {
            Memory hostMemory = hostInstance.memory();
            int fnId = (int) args[0];
            int reqPtr = (int) args[1];
            int reqLen = (int) args[2];
            int respPtr = (int) args[3];
            int respCap = (int) args[4];
            if (reqLen < 0 || respCap < 0 || rangesOverlap(reqPtr, reqLen, respPtr, respCap)) {
                return BlueQuickJsHostDispatcher.TRANSPORT_ERROR;
            }
            byte[] request = hostMemory.readBytes(reqPtr, reqLen);
            BlueQuickJsHostDispatcher.DispatchResult result = dispatcher.dispatch(fnId, request);
            if (result.fatal()) {
                return BlueQuickJsHostDispatcher.TRANSPORT_ERROR;
            }
            byte[] envelope = result.envelope();
            if (envelope.length > respCap) {
                return BlueQuickJsHostDispatcher.TRANSPORT_ERROR;
            }
            hostMemory.write(respPtr, envelope);
            return envelope.length & 0xffffffffL;
        } catch (RuntimeException ex) {
            return BlueQuickJsHostDispatcher.TRANSPORT_ERROR;
        }
    }

    private static boolean rangesOverlap(int aOffset, int aLength, int bOffset, int bLength) {
        if (aLength == 0 || bLength == 0) {
            return false;
        }
        long aStart = aOffset & 0xffffffffL;
        long bStart = bOffset & 0xffffffffL;
        long aEnd = aStart + (aLength & 0xffffffffL);
        long bEnd = bStart + (bLength & 0xffffffffL);
        return aStart < bEnd && bStart < aEnd;
    }

    private int writeBytes(byte[] bytes) {
        int ptr = malloc(bytes.length);
        memory.write(ptr, bytes);
        return ptr;
    }

    private int writeCString(String value) {
        byte[] text = value.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = Arrays.copyOf(text, text.length + 1);
        int ptr = malloc(bytes.length);
        memory.write(ptr, bytes);
        return ptr;
    }

    private String readAndFreeCString(int ptr) {
        try {
            return memory.readCString(ptr, StandardCharsets.UTF_8);
        } finally {
            free(ptr);
        }
    }

    private int malloc(int size) {
        long[] result = malloc.apply(size);
        int ptr = (int) result[0];
        if (ptr == 0) {
            throw new BlueQuickJsResourceException("malloc returned null for " + size + " bytes");
        }
        return ptr;
    }

    private void free(int ptr) {
        if (ptr != 0) {
            free.apply(ptr);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new BlueQuickJsResourceException("wasm instance is closed");
        }
    }
}
