package blue.contract.processor.conversation.javascript.chicory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChicoryHostCallAbiTest {

    @Test
    void validDocumentRequestsReturnEnvelopes() {
        BlueQuickJsHostDispatcher dispatcher = new BlueQuickJsHostDispatcher(bindingsWithDocumentValue("ok"));

        Map<?, ?> envelope = decode(dispatcher.dispatch(HostV1Manifest.DOCUMENT_GET_FN_ID,
                DeterministicValueCodec.encode(Arrays.asList("/value"))));

        assertEquals("ok", envelope.get("ok"));
        assertEquals(1, envelope.get("units"));
    }

    @Test
    void unknownFunctionAndMalformedRequestAreFatalTransportFailures() {
        BlueQuickJsHostDispatcher dispatcher = new BlueQuickJsHostDispatcher(bindingsWithDocumentValue("ok"));

        assertTrue(dispatcher.dispatch(999, DeterministicValueCodec.encode(Collections.emptyList())).fatal());
        assertTrue(dispatcher.dispatch(HostV1Manifest.DOCUMENT_GET_FN_ID, new byte[]{(byte) 0xff}).fatal());
        assertTrue(dispatcher.dispatch(HostV1Manifest.DOCUMENT_GET_FN_ID, DeterministicValueCodec.encode("not-array")).fatal());
    }

    @Test
    void responseLargerThanManifestLimitBecomesDeterministicLimitEnvelope() {
        BlueQuickJsHostDispatcher dispatcher = new BlueQuickJsHostDispatcher(
                bindingsWithDocumentValue(repeat('x', 300000)));

        Map<?, ?> envelope = decode(dispatcher.dispatch(HostV1Manifest.DOCUMENT_GET_FN_ID,
                DeterministicValueCodec.encode(Arrays.asList("/value"))));

        assertEquals(0, envelope.get("units"));
        assertEquals("LIMIT_EXCEEDED", ((Map<?, ?>) envelope.get("err")).get("code"));
    }

    @Test
    void requestLargerThanManifestLimitBecomesDeterministicLimitEnvelope() {
        BlueQuickJsHostDispatcher dispatcher = new BlueQuickJsHostDispatcher(bindingsWithDocumentValue("ok"));

        Map<?, ?> envelope = decode(dispatcher.dispatch(HostV1Manifest.DOCUMENT_GET_FN_ID,
                DeterministicValueCodec.encode(Arrays.asList(repeat('p', 5000)))));

        assertEquals(0, envelope.get("units"));
        assertEquals("LIMIT_EXCEEDED", ((Map<?, ?>) envelope.get("err")).get("code"));
    }

    @Test
    void reentrantHostCallIsFatalTransportFailure() throws Exception {
        BlueQuickJsHostDispatcher dispatcher = new BlueQuickJsHostDispatcher(bindingsWithDocumentValue("ok"));
        Field inProgress = BlueQuickJsHostDispatcher.class.getDeclaredField("inProgress");
        inProgress.setAccessible(true);
        inProgress.set(dispatcher, Boolean.TRUE);

        BlueQuickJsHostDispatcher.DispatchResult result = dispatcher.dispatch(HostV1Manifest.DOCUMENT_GET_FN_ID,
                DeterministicValueCodec.encode(Arrays.asList("/value")));

        assertTrue(result.fatal());
        assertTrue(result.error().contains("reentrant"));
    }

    @Test
    void hostDispatcherNeverThrowsForInternalFailures() {
        BlueQuickJsHostDispatcher dispatcher = new BlueQuickJsHostDispatcher(null);

        BlueQuickJsHostDispatcher.DispatchResult result = dispatcher.dispatch(HostV1Manifest.DOCUMENT_GET_FN_ID, null);

        assertTrue(result.fatal());
    }

    private static Map<?, ?> decode(BlueQuickJsHostDispatcher.DispatchResult result) {
        assertFalse(result.fatal(), result.error());
        return (Map<?, ?>) DeterministicValueCodec.decode(result.envelope());
    }

    private static Map<String, Object> bindingsWithDocumentValue(Object value) {
        Map<String, Object> document = new LinkedHashMap<String, Object>();
        document.put("value", value);
        Map<String, Object> bindings = new LinkedHashMap<String, Object>();
        bindings.put("document", document);
        bindings.put("documentCanonical", document);
        bindings.put("documentMetadata", Collections.emptyMap());
        return bindings;
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
