package blue.contract.processor.conversation.javascript.chicory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueQuickJsResultParserTest {

    @Test
    void parsesSuccessfulResultWithGas() {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("a", 1);
        value.put("b", Arrays.asList(Boolean.TRUE, null));
        String payload = hex(DeterministicValueCodec.encode(value));

        BlueQuickJsResultParser.ParsedResult result =
                BlueQuickJsResultParser.parse("RESULT " + payload + " GAS remaining=10 used=5");

        assertTrue(result.ok());
        assertEquals(value, result.value());
        assertEquals(10L, result.gasRemaining());
        assertEquals(5L, result.wasmGasUsed());
    }

    @Test
    void parsesErrorResultWithGas() {
        BlueQuickJsResultParser.ParsedResult result =
                BlueQuickJsResultParser.parse("ERROR TypeError: boom GAS remaining=0 used=7");

        assertFalse(result.ok());
        assertEquals("TypeError: boom", result.errorMessage());
        assertEquals(0L, result.gasRemaining());
        assertEquals(7L, result.wasmGasUsed());
    }

    @Test
    void rejectsMalformedOutput() {
        assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsResultParser.parse("UNKNOWN f6 GAS remaining=1 used=1"));
        assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsResultParser.parse("RESULT f6"));
        assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsResultParser.parse("RESULT f GAS remaining=1 used=1"));
        assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsResultParser.parse("RESULT xx GAS remaining=1 used=1"));
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }
}
