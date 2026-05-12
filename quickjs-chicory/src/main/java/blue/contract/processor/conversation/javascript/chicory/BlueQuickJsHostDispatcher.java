package blue.contract.processor.conversation.javascript.chicory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlueQuickJsHostDispatcher {
    public static final long TRANSPORT_ERROR = 0xffffffffL;
    private static final String LIMIT_EXCEEDED = "LIMIT_EXCEEDED";
    private static final Set<String> BLUE_METADATA_KEYS = Collections.unmodifiableSet(new LinkedHashSet<String>(
            Arrays.asList("name",
                    "description",
                    "type",
                    "itemType",
                    "keyType",
                    "valueType",
                    "value",
                    "items",
                    "blue",
                    "blueId",
                    "schema",
                    "mergePolicy",
                    "$previous",
                    "$pos")));

    private final Map<String, Object> bindings;
    private boolean inProgress;

    public BlueQuickJsHostDispatcher(Map<String, Object> bindings) {
        this.bindings = bindings == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(bindings));
    }

    public synchronized DispatchResult dispatch(int fnId, byte[] requestBytes) {
        if (inProgress) {
            return DispatchResult.fatal("reentrant host_call");
        }
        inProgress = true;
        try {
            return dispatchInternal(fnId, requestBytes);
        } catch (RuntimeException ex) {
            return DispatchResult.fatal("host dispatcher threw: " + ex.getMessage());
        } finally {
            inProgress = false;
        }
    }

    public DispatchResult dispatchForTestingWithoutGuard(int fnId, byte[] requestBytes) {
        return dispatchInternal(fnId, requestBytes);
    }

    private DispatchResult dispatchInternal(int fnId, byte[] requestBytes) {
        FunctionSpec spec = FunctionSpec.forFnId(fnId);
        if (spec == null) {
            return DispatchResult.fatal("unknown fn_id " + fnId);
        }
        if (requestBytes == null) {
            return DispatchResult.fatal("request bytes must not be null");
        }
        if (requestBytes.length > spec.maxRequestBytes) {
            return encodeLimit(spec);
        }
        Object decoded;
        try {
            decoded = DeterministicValueCodec.decode(requestBytes);
        } catch (DeterministicValueCodec.DeterministicValueException ex) {
            return DispatchResult.fatal("failed to decode request: " + ex.getMessage());
        }
        if (!(decoded instanceof List)) {
            return DispatchResult.fatal("request must be a DV array");
        }
        List<?> args = (List<?>) decoded;
        if (args.size() != spec.arity) {
            return DispatchResult.fatal("fn_id=" + fnId + " expected " + spec.arity + " args");
        }
        if (fnId == HostV1Manifest.DOCUMENT_GET_FN_ID) {
            return handleDocument(spec, args.get(0), false);
        }
        if (fnId == HostV1Manifest.DOCUMENT_GET_CANONICAL_FN_ID) {
            return handleDocument(spec, args.get(0), true);
        }
        if (fnId == HostV1Manifest.EMIT_FN_ID) {
            return handleEmit(spec);
        }
        return DispatchResult.fatal("unknown fn_id " + fnId);
    }

    private DispatchResult handleDocument(FunctionSpec spec, Object pointer, boolean canonical) {
        if (pointer instanceof String
                && ((String) pointer).getBytes(StandardCharsets.UTF_8).length > spec.argUtf8Max) {
            return encodeLimit(spec);
        }
        Object root = canonical ? bindings.get("documentCanonical") : bindings.get("document");
        Object metadata = bindings.get("documentMetadata");
        Object value = documentResult(root, pointer, canonical, metadata);
        return encodeOk(spec, value, 1);
    }

    private DispatchResult handleEmit(FunctionSpec spec) {
        Map<String, Object> err = new LinkedHashMap<String, Object>();
        err.put("code", LIMIT_EXCEEDED);
        err.put("details", "emit is not available during expression/code evaluation");
        return encodeErr(spec, err, 1);
    }

    private Object documentResult(Object root, Object pointer, boolean canonical, Object metadata) {
        String normalized = normalizePointer(pointer);
        if (!canonical && metadata instanceof Map && ((Map<?, ?>) metadata).containsKey(normalized)) {
            Object value = ((Map<?, ?>) metadata).get(normalized);
            return value == null ? null : value;
        }
        PointerResult resolved = getPointer(root, normalized);
        if (!resolved.found) {
            return null;
        }
        return canonical ? resolved.value : simpleValue(resolved.value);
    }

    private String normalizePointer(Object pointer) {
        if (pointer == null || "".equals(pointer)) {
            return "/";
        }
        if (!(pointer instanceof String)) {
            return null;
        }
        String string = (String) pointer;
        return string.startsWith("/") ? string : "/" + string;
    }

    @SuppressWarnings("unchecked")
    private PointerResult getPointer(Object root, String pointer) {
        if (pointer == null || !pointer.startsWith("/")) {
            return PointerResult.missing();
        }
        if ("/".equals(pointer)) {
            return PointerResult.found(root == null ? null : root);
        }
        Object current = root;
        String[] segments = pointer.substring(1).split("/", -1);
        for (String rawSegment : segments) {
            String segment = rawSegment.replace("~1", "/").replace("~0", "~");
            if (current instanceof List) {
                if (!segment.matches("^(0|[1-9]\\d*)$")) {
                    return PointerResult.missing();
                }
                int index;
                try {
                    index = Integer.parseInt(segment);
                } catch (NumberFormatException ex) {
                    return PointerResult.missing();
                }
                List<?> list = (List<?>) current;
                if (index >= list.size()) {
                    return PointerResult.missing();
                }
                current = list.get(index);
            } else if (current instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) current;
                if (!map.containsKey(segment)) {
                    return PointerResult.missing();
                }
                current = map.get(segment);
            } else {
                return PointerResult.missing();
            }
        }
        return PointerResult.found(current == null ? null : current);
    }

    @SuppressWarnings("unchecked")
    private Object simpleValue(Object node) {
        if (node == null) {
            return null;
        }
        if (node instanceof List) {
            List<Object> result = new ArrayList<Object>();
            for (Object value : (List<?>) node) {
                result.add(simpleValue(value));
            }
            return result;
        }
        if (!(node instanceof Map)) {
            return node;
        }
        Map<String, Object> map = (Map<String, Object>) node;
        if (map.containsKey("value")) {
            Object value = map.get("value");
            return value == null ? null : value;
        }
        Object items = map.get("items");
        if (items instanceof List) {
            List<Object> result = new ArrayList<Object>();
            for (Object value : (List<?>) items) {
                result.add(simpleValue(value));
            }
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!BLUE_METADATA_KEYS.contains(entry.getKey())) {
                result.put(entry.getKey(), simpleValue(entry.getValue()));
            }
        }
        return result;
    }

    private DispatchResult encodeOk(FunctionSpec spec, Object value, int units) {
        Map<String, Object> envelope = new LinkedHashMap<String, Object>();
        envelope.put("ok", value);
        envelope.put("units", units);
        return encodeEnvelope(spec, envelope, true);
    }

    private DispatchResult encodeErr(FunctionSpec spec, Map<String, Object> err, int units) {
        Map<String, Object> envelope = new LinkedHashMap<String, Object>();
        envelope.put("err", err);
        envelope.put("units", units);
        return encodeEnvelope(spec, envelope, true);
    }

    private DispatchResult encodeLimit(FunctionSpec spec) {
        Map<String, Object> err = new LinkedHashMap<String, Object>();
        err.put("code", LIMIT_EXCEEDED);
        Map<String, Object> envelope = new LinkedHashMap<String, Object>();
        envelope.put("err", err);
        envelope.put("units", 0);
        return encodeEnvelope(spec, envelope, false);
    }

    private DispatchResult encodeEnvelope(FunctionSpec spec, Map<String, Object> envelope, boolean allowLimitFallback) {
        byte[] bytes;
        try {
            bytes = DeterministicValueCodec.encode(envelope);
        } catch (DeterministicValueCodec.DeterministicValueException ex) {
            return allowLimitFallback ? encodeLimit(spec) : DispatchResult.fatal("failed to encode envelope: " + ex.getMessage());
        }
        if (bytes.length > spec.maxResponseBytes) {
            return allowLimitFallback ? encodeLimit(spec) : DispatchResult.fatal("response exceeds max_response_bytes");
        }
        return DispatchResult.response(bytes);
    }

    public static final class DispatchResult {
        private final boolean fatal;
        private final byte[] envelope;
        private final String error;

        private DispatchResult(boolean fatal, byte[] envelope, String error) {
            this.fatal = fatal;
            this.envelope = envelope == null ? null : envelope.clone();
            this.error = error;
        }

        private static DispatchResult response(byte[] envelope) {
            return new DispatchResult(false, envelope, null);
        }

        private static DispatchResult fatal(String error) {
            return new DispatchResult(true, null, error);
        }

        public boolean fatal() {
            return fatal;
        }

        public byte[] envelope() {
            return envelope == null ? null : envelope.clone();
        }

        public String error() {
            return error;
        }
    }

    private static final class PointerResult {
        private final boolean found;
        private final Object value;

        private PointerResult(boolean found, Object value) {
            this.found = found;
            this.value = value;
        }

        private static PointerResult found(Object value) {
            return new PointerResult(true, value);
        }

        private static PointerResult missing() {
            return new PointerResult(false, null);
        }
    }

    private static final class FunctionSpec {
        private final int fnId;
        private final int arity;
        private final int maxRequestBytes;
        private final int maxResponseBytes;
        private final int argUtf8Max;

        private FunctionSpec(int fnId, int arity, int maxRequestBytes, int maxResponseBytes, int argUtf8Max) {
            this.fnId = fnId;
            this.arity = arity;
            this.maxRequestBytes = maxRequestBytes;
            this.maxResponseBytes = maxResponseBytes;
            this.argUtf8Max = argUtf8Max;
        }

        private static FunctionSpec forFnId(int fnId) {
            if (fnId == HostV1Manifest.DOCUMENT_GET_FN_ID || fnId == HostV1Manifest.DOCUMENT_GET_CANONICAL_FN_ID) {
                return new FunctionSpec(fnId, 1, 4096, 262144, 2048);
            }
            if (fnId == HostV1Manifest.EMIT_FN_ID) {
                return new FunctionSpec(fnId, 1, 32768, 64, Integer.MAX_VALUE);
            }
            return null;
        }
    }
}
