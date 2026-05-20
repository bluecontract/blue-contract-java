package blue.contract.processor.conversation.workflow;

import blue.language.model.Node;

import java.util.Map;

final class NodeUtil {
    private NodeUtil() {
    }

    static Node property(Node node, String key) {
        if (node == null || node.getProperties() == null) {
            return null;
        }
        return node.getProperties().get(key);
    }

    static boolean isEmpty(Node node) {
        return node == null
                || (node.getValue() == null
                && empty(node.getItems())
                && empty(node.getProperties()));
    }

    static Object rawScalar(Node node) {
        if (node == null) {
            return null;
        }
        if (node.getValue() != null) {
            return node.getValue();
        }
        if (node.getProperties() != null && node.getProperties().containsKey("value")) {
            return rawScalar(node.getProperties().get("value"));
        }
        return null;
    }

    static String text(Node node) {
        Object raw = rawScalar(node);
        return raw != null ? String.valueOf(raw) : null;
    }

    static String textProperty(Node node, String key) {
        return text(property(node, key));
    }

    static boolean booleanProperty(Node node, String key, boolean defaultValue) {
        Object raw = rawScalar(property(node, key));
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean) {
            return ((Boolean) raw).booleanValue();
        }
        if (raw instanceof String) {
            return Boolean.parseBoolean((String) raw);
        }
        return defaultValue;
    }

    private static boolean empty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    private static boolean empty(Iterable<?> items) {
        return items == null || !items.iterator().hasNext();
    }
}
