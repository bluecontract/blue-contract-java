package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.JavaScriptExecutionException;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.javascript.QuickJsGas;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChicoryBlueQuickJsRuntime implements JavaScriptRuntime, AutoCloseable {
    private final BlueQuickJsWasmRuntimeConfig config;

    public ChicoryBlueQuickJsRuntime() {
        this(BlueQuickJsWasmRuntimeConfig.defaultConfig());
    }

    public ChicoryBlueQuickJsRuntime(BlueQuickJsWasmRuntimeConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    @Override
    public JavaScriptEvaluationResult evaluate(JavaScriptEvaluationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        long wasmGasLimit = QuickJsGas.toWasmFuel(request.hostGasLimit());
        BlueQuickJsWasmResources resources = BlueQuickJsWasmResources.resolve(config);
        Map<String, Object> bindings = request.bindings();
        BlueQuickJsHostDispatcher dispatcher = new BlueQuickJsHostDispatcher(bindings);
        byte[] contextBlob = DeterministicValueCodec.encode(contextEnvelope(bindings));
        String source = BlueQuickJsSourceWrapper.wrap(request);

        try (BlueQuickJsWasmInstance instance = new BlueQuickJsWasmInstance(resources, dispatcher)) {
            instance.initialize(HostV1Manifest.bytes(), HostV1Manifest.HOST_V1_HASH, contextBlob, wasmGasLimit);
            BlueQuickJsResultParser.ParsedResult parsed = BlueQuickJsResultParser.parse(instance.eval(source));
            if (!parsed.ok()) {
                throw new JavaScriptExecutionException(normalizeVmError(parsed.errorMessage()));
            }
            return new JavaScriptEvaluationResult(parsed.value(),
                    parsed.wasmGasUsed(),
                    QuickJsGas.toHostGasUsed(parsed.wasmGasUsed()));
        } catch (JavaScriptExecutionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new JavaScriptExecutionException("Chicory blue-quickjs evaluation failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void close() {
        // Fresh Wasm instances are used per evaluation for now.
    }

    private static Map<String, Object> contextEnvelope(Map<String, Object> bindings) {
        Map<String, Object> source = bindings == null ? Collections.<String, Object>emptyMap() : bindings;
        Map<String, Object> envelope = new LinkedHashMap<String, Object>();
        envelope.put("event", valueOrNull(source.get("event")));
        envelope.put("eventCanonical", valueOrNull(source.get("eventCanonical")));
        Object steps = source.get("steps");
        envelope.put("steps", steps == null ? Collections.emptyList() : steps);
        envelope.put("currentContract", valueOrNull(source.get("currentContract")));
        envelope.put("currentContractCanonical", valueOrNull(source.get("currentContractCanonical")));
        return envelope;
    }

    private static Object valueOrNull(Object value) {
        return value == null ? null : value;
    }

    private static String normalizeVmError(String message) {
        if ("OutOfGas: out of gas".equals(message)) {
            return "vm-error: out of gas";
        }
        if (message != null && message.startsWith("vm-error: ")) {
            return message;
        }
        return "vm-error: " + (message == null ? "unknown error" : message);
    }
}
