package blue.contract.processor.conversation.javascript.chicory;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostV1ManifestTest {

    @Test
    void embeddedManifestHashMatchesHostV1Hash() {
        assertEquals("Host.v1", HostV1Manifest.ABI_ID);
        assertEquals(1, HostV1Manifest.ABI_VERSION);
        assertEquals(HostV1Manifest.HOST_V1_HASH,
                BlueQuickJsWasmResources.sha256Hex(HostV1Manifest.bytes()));
    }

    @Test
    void functionIdsAreStable() {
        assertEquals(1, HostV1Manifest.DOCUMENT_GET_FN_ID);
        assertEquals(2, HostV1Manifest.DOCUMENT_GET_CANONICAL_FN_ID);
        assertEquals(3, HostV1Manifest.EMIT_FN_ID);
    }

    @Test
    void reservedTransportErrorsAreNotDeclaredAsBusinessErrors() {
        byte[] bytes = HostV1Manifest.bytes();
        String manifestAscii = new String(bytes, StandardCharsets.ISO_8859_1);

        assertFalse(manifestAscii.contains("HOST_TRANSPORT"));
        assertFalse(manifestAscii.contains("HOST_ENVELOPE_INVALID"));
        assertTrue(manifestAscii.contains("Host.v1"));
        assertTrue(manifestAscii.contains("document"));
        assertTrue(manifestAscii.contains("getCanonical"));
        assertTrue(manifestAscii.contains("emit"));
    }
}
