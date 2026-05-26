package blue.contract.processor.expression;

import blue.contract.processor.conversation.bex.BexExpressionEnabledFields;
import blue.language.NodeProvider;
import blue.language.merge.MergingProcessor;
import blue.language.merge.NodeResolver;
import blue.language.model.Node;
import blue.language.utils.NodePathAccessor;
import blue.language.utils.NodePathEditor;
import blue.repo.conversation.Compute;

import java.util.List;
import java.util.Map;

public final class ExpressionPreservingMergingProcessor implements MergingProcessor {
    private final MergingProcessor delegate;
    private final BexExpressionEnabledFields expressionEnabledFields = new BexExpressionEnabledFields();

    public ExpressionPreservingMergingProcessor(MergingProcessor delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public void process(Node target, Node source, NodeProvider nodeProvider, NodeResolver nodeResolver) {
        if (isFullExpression(source)) {
            target.replaceWith(new Node().value(source.getRawValue()));
            return;
        }
        stripComputeRuntimeDefaults(target, source);
        preserveExpressionEnabledFields(target, source);
        delegate.process(target, source, nodeProvider, nodeResolver);
    }

    @Override
    public void postProcess(Node target, Node source, NodeProvider nodeProvider, NodeResolver nodeResolver) {
        if (isFullExpression(source)) {
            if (!source.getRawValue().equals(target.getRawValue())) {
                target.replaceWith(new Node().value(source.getRawValue()));
            }
            return;
        }
        stripComputeRuntimeDefaults(target, source);
        preserveExpressionEnabledFields(target, source);
        delegate.postProcess(target, source, nodeProvider, nodeResolver);
        preserveAuthoredMetadata(target, source);
    }

    private boolean isFullExpression(Node node) {
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

    private void preserveAuthoredMetadata(Node target, Node source) {
        if (source.getName() != null && target.getName() == null) {
            target.name(source.getName());
        }
        if (source.getDescription() != null && target.getDescription() == null) {
            target.description(source.getDescription());
        }
    }

    private void preserveExpressionEnabledFields(Node target, Node source) {
        List<String> paths = expressionEnabledFields.preservedPathsForStep(source);
        if (paths.isEmpty()) {
            return;
        }
        for (String path : paths) {
            Node preserved = NodePathAccessor.getNode(source, path);
            if (preserved != null) {
                NodePathEditor.put(target, path, preserved.clone());
            }
        }
    }

    private void stripComputeRuntimeDefaults(Node target, Node source) {
        if (!isComputeMerge(target, source)) {
            return;
        }
        stripRuntimeDefault(target, source, "emitEvents");
        stripRuntimeDefault(target, source, "returnResult");
    }

    private boolean isComputeMerge(Node target, Node source) {
        if (target == null || source == null || target.getProperties() == null || source.getProperties() == null) {
            return false;
        }
        if (!source.getProperties().containsKey("emitEvents") && !source.getProperties().containsKey("returnResult")) {
            return false;
        }
        return hasTypeBlueId(source, Compute.blueId())
                || hasTypeBlueId(target, Compute.blueId())
                || ("Compute".equals(target.getName())
                && target.getProperties().containsKey("emitEvents")
                && target.getProperties().containsKey("returnResult"));
    }

    private void stripRuntimeDefault(Node target, Node source, String key) {
        Map<String, Node> targetProperties = target.getProperties();
        Map<String, Node> sourceProperties = source.getProperties();
        Node sourceValue = sourceProperties.get(key);
        if (sourceValue == null || sourceValue.getValue() == null) {
            return;
        }
        Node targetValue = targetProperties.get(key);
        if (targetValue == null || !Boolean.TRUE.equals(targetValue.getValue())) {
            return;
        }
        Node stripped = targetValue.clone();
        stripped.value((Object) null);
        targetProperties.put(key, stripped);
    }

    private boolean hasTypeBlueId(Node node, String blueId) {
        return node != null
                && node.getType() != null
                && blueId.equals(node.getType().getBlueId());
    }
}
