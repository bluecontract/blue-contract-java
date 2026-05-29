package blue.coordination.processor.workflow;

import blue.language.snapshot.FrozenNode;

import java.math.BigInteger;

final class FrozenNodeUtil {
    private FrozenNodeUtil() {
    }

    static FrozenNode property(FrozenNode node, String key) {
        return node != null && node.getProperties() != null ? node.getProperties().get(key) : null;
    }

    static boolean isEmpty(FrozenNode node) {
        return node == null || (node.getValue() == null
                && (node.getItems() == null || node.getItems().isEmpty())
                && (node.getProperties() == null || node.getProperties().isEmpty()));
    }

    static Object rawScalar(FrozenNode node) {
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

    static String text(FrozenNode node) {
        Object raw = rawScalar(node);
        return raw != null ? String.valueOf(raw) : null;
    }

    static String textProperty(FrozenNode node, String key) {
        return text(property(node, key));
    }

    static boolean booleanProperty(FrozenNode node, String key, boolean defaultValue) {
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

    static Long integer(FrozenNode node) {
        Object raw = rawScalar(node);
        if (raw instanceof BigInteger) {
            return Long.valueOf(((BigInteger) raw).longValue());
        }
        if (raw instanceof Number) {
            return Long.valueOf(((Number) raw).longValue());
        }
        if (raw instanceof String) {
            try {
                return Long.valueOf((String) raw);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
