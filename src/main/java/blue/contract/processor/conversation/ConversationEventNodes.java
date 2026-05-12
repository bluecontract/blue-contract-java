package blue.contract.processor.conversation;

import blue.language.model.Node;
import blue.repo.v1_2_0.conversation.ChatMessage;
import blue.repo.v1_2_0.conversation.OperationRequest;
import blue.repo.v1_2_0.conversation.StatusCompleted;
import blue.repo.v1_2_0.conversation.Timeline;
import blue.repo.v1_2_0.conversation.TimelineEntry;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

final class ConversationEventNodes {
    private ConversationEventNodes() {
    }

    static TimelineEntry timelineEntry(Node node) {
        if (!isTimelineEntry(node)) {
            return null;
        }
        TimelineEntry entry = new TimelineEntry();
        String timelineId = timelineId(node);
        if (timelineId != null) {
            entry.timeline(new Timeline().timelineId(timelineId));
        }
        BigInteger timestamp = timestamp(node);
        if (timestamp != null) {
            entry.timestamp(timestamp);
        }
        return entry;
    }

    static boolean isTimelineEntry(Node node) {
        if (node == null) {
            return false;
        }
        String typeBlueId = typeBlueId(node);
        if (typeBlueId != null) {
            return TimelineEntry.blueId().equals(typeBlueId);
        }
        String typeName = typeInlineValue(node);
        if (typeName != null) {
            return TimelineEntry.qualifiedName().equals(typeName);
        }
        return hasTimelineEntryShape(node);
    }

    static String timelineId(Node node) {
        Node timeline = property(node, "timeline");
        Node timelineId = property(timeline, "timelineId");
        Object value = timelineId != null ? timelineId.getValue() : null;
        return value instanceof String ? (String) value : null;
    }

    static BigInteger timestamp(Node node) {
        Node timestamp = property(node, "timestamp");
        Object value = timestamp != null ? timestamp.getValue() : null;
        if (value instanceof BigInteger) {
            return (BigInteger) value;
        }
        if (value instanceof Number) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        if (value instanceof String) {
            try {
                return new BigInteger((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static boolean matchesPattern(Node node, Node pattern) {
        if (pattern == null) {
            return true;
        }
        if (node == null) {
            return false;
        }
        if (pattern.isReferenceOnly()) {
            return pattern.getBlueId().equals(node.getBlueId());
        }
        if (!typeMatches(node.getType(), pattern.getType())) {
            return false;
        }
        if (!valueMatches(node.getValue(), pattern.getValue())) {
            return false;
        }
        if (!itemsMatch(node.getItems(), pattern.getItems())) {
            return false;
        }
        return propertiesMatch(node.getProperties(), pattern.getProperties());
    }

    private static boolean hasTimelineEntryShape(Node node) {
        Map<String, Node> properties = node.getProperties();
        return properties != null
                && properties.containsKey("timeline")
                && (properties.containsKey("message")
                || properties.containsKey("timestamp")
                || properties.containsKey("prevEntry")
                || properties.containsKey("actor")
                || properties.containsKey("source"));
    }

    private static Node property(Node node, String key) {
        if (node == null || node.getProperties() == null) {
            return null;
        }
        return node.getProperties().get(key);
    }

    private static String typeBlueId(Node node) {
        Node type = node.getType();
        return type != null ? type.getBlueId() : null;
    }

    private static String typeInlineValue(Node node) {
        Node type = node.getType();
        Object value = type != null ? type.getValue() : null;
        return value instanceof String ? (String) value : null;
    }

    private static boolean typeMatches(Node nodeType, Node patternType) {
        if (patternType == null) {
            return true;
        }
        String expected = typeIdentity(patternType);
        if (expected == null) {
            return true;
        }
        if (nodeType == null) {
            return true;
        }
        String actual = typeIdentity(nodeType);
        return expected.equals(actual);
    }

    private static String typeIdentity(Node type) {
        if (type == null) {
            return null;
        }
        if (type.getBlueId() != null) {
            return type.getBlueId();
        }
        Object value = type.getValue();
        if (value instanceof String) {
            String knownBlueId = knownConversationTypeBlueId((String) value);
            return knownBlueId != null ? knownBlueId : (String) value;
        }
        return null;
    }

    private static String knownConversationTypeBlueId(String qualifiedName) {
        if (TimelineEntry.qualifiedName().equals(qualifiedName)) {
            return TimelineEntry.blueId();
        }
        if (ChatMessage.qualifiedName().equals(qualifiedName)) {
            return ChatMessage.blueId();
        }
        if (OperationRequest.qualifiedName().equals(qualifiedName)) {
            return OperationRequest.blueId();
        }
        if (StatusCompleted.qualifiedName().equals(qualifiedName)) {
            return StatusCompleted.blueId();
        }
        return null;
    }

    private static boolean valueMatches(Object actual, Object expected) {
        if (expected == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        if (actual instanceof Number && expected instanceof Number) {
            return number(actual).compareTo(number(expected)) == 0;
        }
        return expected.equals(actual);
    }

    private static BigDecimal number(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        return new BigDecimal(value.toString());
    }

    private static boolean itemsMatch(List<Node> actual, List<Node> expected) {
        if (expected == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            Node expectedItem = expected.get(i);
            if (i < actual.size()) {
                if (!matchesPattern(actual.get(i), expectedItem)) {
                    return false;
                }
            } else if (requiresPresence(expectedItem)) {
                return false;
            }
        }
        return true;
    }

    private static boolean propertiesMatch(Map<String, Node> actual, Map<String, Node> expected) {
        if (expected == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        for (Map.Entry<String, Node> entry : expected.entrySet()) {
            Node actualProperty = actual.get(entry.getKey());
            Node expectedProperty = entry.getValue();
            if (actualProperty != null) {
                if (!matchesPattern(actualProperty, expectedProperty)) {
                    return false;
                }
            } else if (requiresPresence(expectedProperty)) {
                return false;
            }
        }
        return true;
    }

    private static boolean requiresPresence(Node pattern) {
        if (pattern == null) {
            return false;
        }
        if (pattern.isReferenceOnly() || pattern.getValue() != null) {
            return true;
        }
        if (pattern.getItems() != null) {
            for (Node item : pattern.getItems()) {
                if (requiresPresence(item)) {
                    return true;
                }
            }
        }
        if (pattern.getProperties() != null) {
            for (Node property : pattern.getProperties().values()) {
                if (requiresPresence(property)) {
                    return true;
                }
            }
        }
        return false;
    }
}
