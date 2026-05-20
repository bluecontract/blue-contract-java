package blue.contract.processor.conversation.workflow;

import blue.language.model.Node;
import blue.language.snapshot.FrozenNode;

final class ComputeDefinitionResolver {
    FrozenNode resolve(FrozenNode stepNode, StepExecutionContext context) {
        FrozenNode definition = FrozenNodeUtil.property(stepNode, "definition");
        if (definition == null || FrozenNodeUtil.isEmpty(definition)) {
            return null;
        }
        String text = FrozenNodeUtil.text(definition);
        if (text != null && !text.trim().isEmpty()) {
            String pointer = resolvePointer(text.trim(), context);
            FrozenNode frozen = context.processorContext().canonicalFrozenAt(pointer);
            if (frozen == null) {
                context.processorContext().throwFatal("Compute definition not found: " + text);
                return null;
            }
            return frozen;
        }
        return definition;
    }

    FrozenNode resolve(Node stepNode, StepExecutionContext context) {
        Node definition = NodeUtil.property(stepNode, "definition");
        if (definition == null || NodeUtil.isEmpty(definition)) {
            return null;
        }
        String text = NodeUtil.text(definition);
        if (text != null && !text.trim().isEmpty()) {
            String pointer = resolvePointer(text.trim(), context);
            FrozenNode frozen = context.processorContext().canonicalFrozenAt(pointer);
            if (frozen == null) {
                context.processorContext().throwFatal("Compute definition not found: " + text);
                return null;
            }
            return frozen;
        }
        return FrozenNode.fromResolvedNode(definition);
    }

    String resolvePointer(String reference, StepExecutionContext context) {
        if (reference.startsWith("/")) {
            return reference;
        }
        String parent = parentPointer(currentContractPointer(context));
        if (parent == null || parent.isEmpty()) {
            parent = "/";
        }
        return appendPointer(parent, reference);
    }

    private String currentContractPointer(StepExecutionContext context) {
        String key = context.processorContext().contractKey();
        if (key == null || key.trim().isEmpty()) {
            return context.processorContext().scopePath();
        }
        String scope = context.processorContext().scopePath();
        String contracts = appendPointer(scope == null || scope.trim().isEmpty() ? "/" : scope, "contracts");
        return appendPointer(contracts, key.trim());
    }

    private String parentPointer(String pointer) {
        if (pointer == null || pointer.isEmpty() || "/".equals(pointer)) {
            return "/";
        }
        int last = pointer.lastIndexOf('/');
        if (last <= 0) {
            return "/";
        }
        return pointer.substring(0, last);
    }

    private String appendPointer(String parent, String segment) {
        String escaped = escapePointerSegment(segment);
        if (parent == null || parent.isEmpty() || "/".equals(parent)) {
            return "/" + escaped;
        }
        return parent + "/" + escaped;
    }

    private String escapePointerSegment(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }
}
