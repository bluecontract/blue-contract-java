package blue.contract.processor.expression;

import blue.language.NodeProvider;
import blue.language.merge.MergingProcessor;
import blue.language.merge.NodeResolver;
import blue.language.model.Node;

public final class ExpressionPreservingMergingProcessor implements MergingProcessor {
    private final MergingProcessor delegate;

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
}
