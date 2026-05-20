package blue.contract.processor.conversation.bex;

import blue.language.model.Node;
import blue.repo.conversation.TriggerEvent;
import blue.repo.conversation.UpdateDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BexExpressionEnabledFields {
    private final BexExpressionDetector detector;

    public BexExpressionEnabledFields() {
        this(new BexExpressionDetector());
    }

    public BexExpressionEnabledFields(BexExpressionDetector detector) {
        if (detector == null) {
            throw new IllegalArgumentException("detector must not be null");
        }
        this.detector = detector;
    }

    public List<String> preservedPathsForStep(Node stepNode) {
        if (stepNode == null) {
            return Collections.emptyList();
        }
        List<String> paths = new ArrayList<String>(1);
        if (isStepType(stepNode, UpdateDocument.qualifiedName(), UpdateDocument.blueId())) {
            Node changeset = property(stepNode, "changeset");
            if (detector.containsBex(changeset) || isFullLegacyExpression(changeset)) {
                paths.add("/changeset");
            }
        } else if (isStepType(stepNode, TriggerEvent.qualifiedName(), TriggerEvent.blueId())) {
            Node event = property(stepNode, "event");
            if (detector.containsBex(event) || isFullLegacyExpression(event)) {
                paths.add("/event");
            }
        }
        return paths;
    }

    private boolean isStepType(Node stepNode, String qualifiedName, String blueId) {
        Node type = stepNode.getType();
        if (type == null) {
            return false;
        }
        if (blueId.equals(type.getBlueId())) {
            return true;
        }
        Object value = type.getValue();
        return qualifiedName.equals(value);
    }

    private Node property(Node node, String key) {
        return node != null && node.getProperties() != null ? node.getProperties().get(key) : null;
    }

    private boolean isFullLegacyExpression(Node node) {
        if (node == null) {
            return false;
        }
        Object value = node.getRawValue();
        if (!(value instanceof String)) {
            return false;
        }
        String text = ((String) value).trim();
        return text.startsWith("${")
                && text.endsWith("}")
                && text.indexOf("${") == text.lastIndexOf("${");
    }
}
