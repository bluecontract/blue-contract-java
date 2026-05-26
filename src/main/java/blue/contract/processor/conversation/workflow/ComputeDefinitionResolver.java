package blue.contract.processor.conversation.workflow;

import blue.contract.processor.conversation.bex.BexProcessingMetrics;
import blue.language.model.Node;
import blue.language.snapshot.FrozenNode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class ComputeDefinitionResolver {
    private final BexProcessingMetrics metrics;
    private final ConcurrentMap<String, FrozenNode> cache = new ConcurrentHashMap<String, FrozenNode>();

    ComputeDefinitionResolver() {
        this(null);
    }

    ComputeDefinitionResolver(BexProcessingMetrics metrics) {
        this.metrics = metrics;
    }

    FrozenNode resolve(FrozenNode stepNode, StepExecutionContext context) {
        FrozenNode definition = FrozenNodeUtil.property(stepNode, "definition");
        if (definition == null || FrozenNodeUtil.isEmpty(definition)) {
            return null;
        }
        String text = FrozenNodeUtil.text(definition);
        if (text != null && !text.trim().isEmpty()) {
            String pointer = resolvePointer(text.trim(), context);
            FrozenNode frozen = cachedFrozenAt(pointer, context);
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
            FrozenNode frozen = cachedFrozenAt(pointer, context);
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

    private FrozenNode cachedFrozenAt(String pointer, StepExecutionContext context) {
        String key = cacheKey(pointer, context);
        FrozenNode cached = cache.get(key);
        if (cached != null) {
            if (metrics != null) {
                metrics.incrementComputeDefinitionResolveHits();
            }
            return cached;
        }
        FrozenNode frozen = context.processorContext().canonicalFrozenAt(pointer);
        if (frozen != null) {
            cache.putIfAbsent(key, frozen);
        }
        if (metrics != null) {
            metrics.incrementComputeDefinitionResolveMisses();
        }
        return frozen;
    }

    private String cacheKey(String pointer, StepExecutionContext context) {
        FrozenNode contract = context.currentContractFrozenNode();
        String contractId = contract != null && contract.blueId() != null
                ? contract.blueId()
                : String.valueOf(context.processorContext().scopePath()) + ":" + String.valueOf(context.processorContext().contractKey());
        return contractId + "|" + pointer;
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
