package blue.contract.processor.conversation.bex;

import blue.language.model.Node;
import blue.language.snapshot.FrozenNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BexExpressionDetectorTest {
    private final BexExpressionDetector detector = new BexExpressionDetector();

    @Test
    void detectsFullFieldBindingExpression() {
        Node node = obj("$binding", obj("name", value("steps"), "path", value("/BuildPatch/changeset")));

        assertTrue(detector.containsBex(node));
        assertTrue(detector.isBexOperatorObject(node));
        assertTrue(detector.containsBex(FrozenNode.fromResolvedNode(node)));
        assertTrue(detector.isBexOperatorObject(FrozenNode.fromResolvedNode(node)));
    }

    @Test
    void detectsNestedBindingExpression() {
        Node node = obj("type", value("Conversation/Event"),
                "kind", obj("$binding", obj("name", value("event"), "path", value("/kind"))));

        assertTrue(detector.containsBex(node));
        assertFalse(detector.isBexOperatorObject(node));
    }

    @Test
    void ignoresLegacyStringExpressions() {
        Node node = obj("message", value("${event.kind}"));

        assertFalse(detector.containsBex(node));
        assertFalse(detector.isBexOperatorObject(node));
    }

    @Test
    void treatsLiteralAsOperatorButDoesNotNeedNestedScan() {
        Node node = obj("$literal", obj("$binding", obj("name", value("event"), "path", value("/kind"))));

        assertTrue(detector.containsBex(node));
        assertTrue(detector.isBexOperatorObject(node));
    }

    @Test
    void plainTextThatLooksLikeYamlIsNotBex() {
        Node node = value("$binding: steps");

        assertFalse(detector.containsBex(node));
        assertFalse(detector.isBexOperatorObject(node));
    }

    private static Node obj(String key, Node value) {
        return new Node().properties(key, value);
    }

    private static Node obj(String key1, Node value1, String key2, Node value2) {
        return new Node().properties(key1, value1, key2, value2);
    }

    @SuppressWarnings("unused")
    private static Node list(Node... items) {
        return new Node().items(Arrays.asList(items));
    }

    private static Node value(String value) {
        return new Node().value(value);
    }
}
