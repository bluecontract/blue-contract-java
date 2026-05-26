package blue.contract.processor.conversation.bex;

import blue.language.model.Node;
import blue.language.snapshot.FrozenNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario:
 * The BEX detector decides whether a workflow field should be preserved and evaluated by the BEX field
 * evaluator instead of the legacy string expression path.
 *
 * Main flow:
 * 1. Detect full-field BEX operator objects such as {@code $binding}.
 * 2. Detect nested BEX expressions inside literal objects.
 * 3. Ignore legacy dollar-brace string expressions.
 * 4. Treat {@code $literal} as a BEX operator while not requiring recursive execution checks inside
 *    its payload.
 *
 * Actors and operations:
 * - Update Document and Trigger Event use this detector to decide whether their expression-enabled
 *   fields should run through BEX.
 * - Non-expression fields remain outside this detector-driven evaluation path.
 */
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
