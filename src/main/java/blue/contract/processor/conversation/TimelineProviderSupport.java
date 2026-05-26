package blue.contract.processor.conversation;

import blue.language.model.Node;
import blue.language.processor.ChannelCheckpointContext;
import blue.language.processor.ChannelEvaluation;
import blue.language.processor.ChannelEvaluationContext;
import blue.language.utils.BlueIdCalculator;
import blue.repo.conversation.TimelineChannel;
import java.math.BigInteger;

public final class TimelineProviderSupport {
    private TimelineProviderSupport() {
    }

    public static ChannelEvaluation evaluateTimelineEntry(TimelineChannel contract, ChannelEvaluationContext context) {
        Node eventNode = context.event();
        if (!ConversationEventNodes.isTimelineEntry(eventNode)) {
            return ChannelEvaluation.noMatch();
        }
        if (!matchesTimelineId(contract, eventNode) || !matchesEventFilter(contract, eventNode)) {
            return ChannelEvaluation.noMatch();
        }
        return ChannelEvaluation.match(eventNode);
    }

    public static boolean matchesTimelineId(TimelineChannel contract, Node eventNode) {
        String timelineId = trimToNull(contract.getTimelineId());
        return timelineId == null || timelineId.equals(ConversationEventNodes.timelineId(eventNode));
    }

    public static boolean matchesEventFilter(TimelineChannel contract, Node eventNode) {
        return contract.getEvent() == null || ConversationEventNodes.matchesPattern(eventNode, contract.getEvent());
    }

    public static String eventId(Node eventNode) {
        return eventNode != null ? BlueIdCalculator.calculateBlueId(eventNode.clone().blue(null)) : null;
    }

    public static boolean isNewerOrSameTimelineEvent(ChannelCheckpointContext context) {
        Node currentEvent = context.event();
        Node previousEvent = context.lastEvent();
        BigInteger currentTimestamp = ConversationEventNodes.timestamp(currentEvent);
        if (currentTimestamp == null) {
            return true;
        }
        BigInteger previousTimestamp = ConversationEventNodes.timestamp(previousEvent);
        if (previousTimestamp == null) {
            return true;
        }
        if (currentTimestamp.compareTo(previousTimestamp) == 0
                && ConversationEventNodes.matchesPattern(previousEvent, currentEvent)) {
            return false;
        }
        return currentTimestamp.compareTo(previousTimestamp) >= 0;
    }

    public static Node property(Node node, String key) {
        if (node == null || node.getProperties() == null) {
            return null;
        }
        return node.getProperties().get(key);
    }

    public static String textProperty(Node node, String key) {
        Node property = property(node, key);
        Object value = property != null ? property.getValue() : null;
        return value instanceof String ? (String) value : null;
    }

    public static boolean hasType(Node node, String blueId, String qualifiedName) {
        if (node == null || node.getType() == null) {
            return false;
        }
        Node type = node.getType();
        if (blueId != null && blueId.equals(type.getBlueId())) {
            return true;
        }
        Object value = type.getValue();
        return qualifiedName != null && qualifiedName.equals(value);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
