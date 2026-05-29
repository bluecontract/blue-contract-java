package blue.coordination.processor;

import blue.language.model.Node;
import blue.language.processor.HandlerMatchContext;
import blue.language.processor.model.InitializationMarker;
import blue.language.processor.model.MarkerContract;
import blue.language.utils.BlueIdCalculator;
import blue.repo.coordination.Operation;
import blue.repo.coordination.OperationRequest;
import blue.repo.coordination.SequentialWorkflowOperation;
import java.util.Map;

final class OperationRequestMatcher {

    boolean matches(SequentialWorkflowOperation contract, HandlerMatchContext context) {
        if (contract == null || context == null) {
            return false;
        }
        if (contract.getEvent() != null && !context.matchesEventPattern(contract.getEvent())) {
            return false;
        }
        OperationRequestEvent requestEvent = OperationRequestEvent.from(context.event());
        if (requestEvent == null) {
            return false;
        }
        String operationKey = trimToNull(contract.getOperation());
        if (operationKey == null || !operationKey.equals(requestEvent.operation())) {
            return false;
        }
        Operation operation = operationMarker(context.markers(), operationKey);
        if (operation == null) {
            return false;
        }
        if (!channelsCompatible(contract, operation)) {
            return false;
        }
        if (!pinnedDocumentCompatible(requestEvent, context.markers())) {
            return false;
        }
        return requestMatches(operation.getRequest(), requestEvent, context);
    }

    private boolean requestMatches(Node requestPattern,
                                   OperationRequestEvent requestEvent,
                                   HandlerMatchContext context) {
        if (requestPattern == null) {
            return true;
        }
        if (isEmptyRequestPattern(requestPattern)) {
            return true;
        }
        if (requestEvent.request() == null) {
            return false;
        }
        return context.matchesEventPattern(requestEvent.patternFor(requestPattern));
    }

    private boolean isEmptyRequestPattern(Node requestPattern) {
        return requestPattern.getType() == null
                && requestPattern.getItemType() == null
                && requestPattern.getKeyType() == null
                && requestPattern.getValueType() == null
                && requestPattern.getValue() == null
                && requestPattern.getItems() == null
                && (requestPattern.getProperties() == null || requestPattern.getProperties().isEmpty())
                && requestPattern.getBlueId() == null
                && requestPattern.getSchema() == null;
    }

    private boolean channelsCompatible(SequentialWorkflowOperation contract, Operation operation) {
        String operationChannel = trimToNull(operation.getChannel());
        if (operationChannel == null) {
            return true;
        }
        String handlerChannel = trimToNull(contract.getChannelKey());
        return handlerChannel != null && operationChannel.equals(handlerChannel);
    }

    private boolean pinnedDocumentCompatible(OperationRequestEvent requestEvent,
                                             Map<String, MarkerContract> markers) {
        Boolean allowNewerVersion = requestEvent.allowNewerVersion();
        if (!Boolean.FALSE.equals(allowNewerVersion)) {
            return true;
        }
        Node pinnedDocument = requestEvent.document();
        if (pinnedDocument == null) {
            return true;
        }
        InitializationMarker initialized = initializationMarker(markers);
        if (initialized == null || initialized.getDocumentId() == null) {
            return false;
        }
        return initialized.getDocumentId().equals(BlueIdCalculator.calculateBlueId(pinnedDocument.clone()))
                || initialized.getDocumentId().equals(BlueIdCalculator.calculateBlueId(pinnedDocument.clone().blue(null)));
    }

    private InitializationMarker initializationMarker(Map<String, MarkerContract> markers) {
        if (markers == null) {
            return null;
        }
        MarkerContract marker = markers.get("initialized");
        return marker instanceof InitializationMarker ? (InitializationMarker) marker : null;
    }

    private Operation operationMarker(Map<String, MarkerContract> markers, String key) {
        if (markers == null) {
            return null;
        }
        MarkerContract marker = markers.get(key);
        return marker instanceof Operation ? (Operation) marker : null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class OperationRequestEvent {
        private final boolean timelineMessage;
        private final Node requestNode;

        private OperationRequestEvent(boolean timelineMessage, Node requestNode) {
            this.timelineMessage = timelineMessage;
            this.requestNode = requestNode;
        }

        static OperationRequestEvent from(Node event) {
            if (isOperationRequest(event)) {
                return new OperationRequestEvent(false, event);
            }
            if (!CoordinationEventNodes.isTimelineEntry(event)) {
                return null;
            }
            Node message = property(event, "message");
            return isOperationRequest(message) ? new OperationRequestEvent(true, message) : null;
        }

        String operation() {
            return stringProperty(requestNode, "operation");
        }

        Node request() {
            return property(requestNode, "request");
        }

        Node document() {
            return property(requestNode, "document");
        }

        Boolean allowNewerVersion() {
            Node property = property(requestNode, "allowNewerVersion");
            Object value = property != null ? property.getValue() : null;
            return value instanceof Boolean ? (Boolean) value : null;
        }

        Node patternFor(Node requestPattern) {
            Node request = requestPattern.clone();
            if (!timelineMessage) {
                return new Node().properties("request", request);
            }
            return new Node().properties("message", new Node()
                    .properties("request", request));
        }

        private static boolean isOperationRequest(Node node) {
            if (node == null || node.getType() == null) {
                return false;
            }
            String typeBlueId = node.getType().getBlueId();
            if (typeBlueId != null) {
                return OperationRequest.blueId().equals(typeBlueId);
            }
            Object typeValue = node.getType().getValue();
            return OperationRequest.qualifiedName().equals(typeValue);
        }

        private static Node property(Node node, String key) {
            if (node == null || node.getProperties() == null) {
                return null;
            }
            return node.getProperties().get(key);
        }

        private static String stringProperty(Node node, String key) {
            Node property = property(node, key);
            Object value = property != null ? property.getValue() : null;
            return value instanceof String ? (String) value : null;
        }
    }
}
