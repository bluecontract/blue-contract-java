package blue.contract.processor.conversation.bex;

import blue.language.model.Node;
import blue.language.snapshot.FrozenNode;

import java.util.Map;

public final class BexExpressionDetector {
    public boolean containsBex(Node node) {
        if (node == null) {
            return false;
        }
        if (isBexOperatorObject(node)) {
            return true;
        }
        if (node.getProperties() != null) {
            for (Map.Entry<String, Node> entry : node.getProperties().entrySet()) {
                if (containsBex(entry.getValue())) {
                    return true;
                }
            }
        }
        if (node.getItems() != null) {
            for (Node item : node.getItems()) {
                if (containsBex(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean containsBex(FrozenNode node) {
        if (node == null) {
            return false;
        }
        if (isBexOperatorObject(node)) {
            return true;
        }
        if (node.getProperties() != null) {
            for (Map.Entry<String, FrozenNode> entry : node.getProperties().entrySet()) {
                if (containsBex(entry.getValue())) {
                    return true;
                }
            }
        }
        if (node.getItems() != null) {
            for (FrozenNode item : node.getItems()) {
                if (containsBex(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isBexOperatorObject(Node node) {
        if (node == null || node.getProperties() == null || node.getProperties().size() != 1) {
            return false;
        }
        return node.getProperties().keySet().iterator().next().startsWith("$");
    }

    public boolean isBexOperatorObject(FrozenNode node) {
        if (node == null || node.getProperties() == null || node.getProperties().size() != 1) {
            return false;
        }
        return node.getProperties().keySet().iterator().next().startsWith("$");
    }
}
