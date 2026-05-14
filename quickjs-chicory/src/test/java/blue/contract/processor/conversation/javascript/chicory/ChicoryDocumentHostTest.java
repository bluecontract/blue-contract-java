package blue.contract.processor.conversation.javascript.chicory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChicoryDocumentHostTest {

    @Test
    void documentGetMatchesEvaluateMjsPointerBehavior() {
        BlueQuickJsHostDispatcher dispatcher = new BlueQuickJsHostDispatcher(bindings());

        Object root = call(dispatcher, HostV1Manifest.DOCUMENT_GET_FN_ID, "/");
        assertEquals(Arrays.asList(10, 11), root);
        assertEquals(6, call(dispatcher, HostV1Manifest.DOCUMENT_GET_FN_ID, "/counter"));
        assertEquals(6, call(dispatcher, HostV1Manifest.DOCUMENT_GET_FN_ID, "counter"));
        assertEquals(root, call(dispatcher, HostV1Manifest.DOCUMENT_GET_FN_ID, ""));
        assertFatal(dispatcher, HostV1Manifest.DOCUMENT_GET_FN_ID, null);
        assertEquals(10, call(dispatcher, HostV1Manifest.DOCUMENT_GET_FN_ID, "/items/0"));
        assertEquals(1, call(dispatcher, HostV1Manifest.DOCUMENT_GET_FN_ID, "/a~1b"));
        assertEquals(2, call(dispatcher, HostV1Manifest.DOCUMENT_GET_FN_ID, "/a~0b"));
        assertEquals(null, call(dispatcher, HostV1Manifest.DOCUMENT_GET_FN_ID, "/missing"));
        assertFatal(dispatcher, HostV1Manifest.DOCUMENT_GET_FN_ID, 12);
    }

    @Test
    void documentCanonicalReturnsRawCanonicalNodeAndIgnoresMetadataOverride() {
        BlueQuickJsHostDispatcher dispatcher = new BlueQuickJsHostDispatcher(bindings());

        Map<?, ?> canonicalRoot = (Map<?, ?>) call(dispatcher, HostV1Manifest.DOCUMENT_GET_CANONICAL_FN_ID, "/");
        Map<?, ?> canonicalCounter = (Map<?, ?>) call(dispatcher, HostV1Manifest.DOCUMENT_GET_CANONICAL_FN_ID, "/counter");

        assertEquals("Counter", canonicalRoot.get("name"));
        assertEquals(6, canonicalCounter.get("value"));
        assertEquals("Integer", ((Map<?, ?>) canonicalCounter.get("type")).get("value"));
        assertEquals(6, call(dispatcher, HostV1Manifest.DOCUMENT_GET_CANONICAL_FN_ID, "/counter/value"));
    }

    @Test
    void metadataOverrideOnlyAppliesToNonCanonicalDocumentGet() {
        BlueQuickJsHostDispatcher dispatcher = new BlueQuickJsHostDispatcher(bindings());

        assertEquals("Counter label", call(dispatcher, HostV1Manifest.DOCUMENT_GET_FN_ID, "/counter/name"));
        assertEquals(null, call(dispatcher, HostV1Manifest.DOCUMENT_GET_CANONICAL_FN_ID, "/counter/name"));
    }

    @Test
    void emitReturnsDeterministicLimitErrorEnvelope() {
        BlueQuickJsHostDispatcher dispatcher = new BlueQuickJsHostDispatcher(bindings());
        Map<?, ?> envelope = envelope(dispatcher.dispatch(HostV1Manifest.EMIT_FN_ID,
                DeterministicValueCodec.encode(Arrays.asList("event"))));

        assertEquals(0, envelope.get("units"));
        assertEquals("LIMIT_EXCEEDED", ((Map<?, ?>) envelope.get("err")).get("code"));
    }

    private static Object call(BlueQuickJsHostDispatcher dispatcher, int fnId, Object pointer) {
        Map<?, ?> envelope = envelope(dispatcher.dispatch(fnId, DeterministicValueCodec.encode(Arrays.asList(pointer))));
        assertEquals(1, envelope.get("units"));
        return envelope.get("ok");
    }

    private static void assertFatal(BlueQuickJsHostDispatcher dispatcher, int fnId, Object pointer) {
        BlueQuickJsHostDispatcher.DispatchResult result = dispatcher.dispatch(fnId,
                DeterministicValueCodec.encode(Arrays.asList(pointer)));
        org.junit.jupiter.api.Assertions.assertTrue(result.fatal(), "expected fatal transport failure");
    }

    private static Map<?, ?> envelope(BlueQuickJsHostDispatcher.DispatchResult result) {
        assertFalse(result.fatal(), result.error());
        return (Map<?, ?>) DeterministicValueCodec.decode(result.envelope());
    }

    private static Map<String, Object> bindings() {
        Map<String, Object> counter = new LinkedHashMap<String, Object>();
        counter.put("type", textNode("Integer"));
        counter.put("value", 6);

        Map<String, Object> item0 = new LinkedHashMap<String, Object>();
        item0.put("value", 10);
        Map<String, Object> item1 = new LinkedHashMap<String, Object>();
        item1.put("value", 11);

        Map<String, Object> document = new LinkedHashMap<String, Object>();
        document.put("name", "Counter");
        document.put("counter", counter);
        document.put("items", Arrays.asList(item0, item1));
        document.put("a/b", singletonValue(1));
        document.put("a~b", singletonValue(2));

        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("/counter/name", "Counter label");

        Map<String, Object> bindings = new LinkedHashMap<String, Object>();
        bindings.put("document", document);
        bindings.put("documentCanonical", document);
        bindings.put("documentMetadata", metadata);
        return bindings;
    }

    private static Map<String, Object> textNode(String value) {
        Map<String, Object> node = new LinkedHashMap<String, Object>();
        node.put("value", value);
        return node;
    }

    private static Map<String, Object> singletonValue(int value) {
        Map<String, Object> node = new LinkedHashMap<String, Object>();
        node.put("value", value);
        return node;
    }
}
