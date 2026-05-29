package blue.coordination.processor.merge;

import blue.language.NodeProvider;
import blue.language.merge.MergingProcessor;
import blue.language.merge.NodeResolver;
import blue.language.model.Node;
import blue.language.utils.NodePathAccessor;
import blue.language.utils.NodePathEditor;
import blue.repo.coordination.Compute;
import blue.repo.coordination.ComputeDefinition;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ComputeRuntimeDefaultMergingProcessor implements MergingProcessor {
    private final MergingProcessor delegate;
    private final ThreadLocal<Map<Node, Map<String, Node>>> suppressedComputeFields =
            ThreadLocal.withInitial(IdentityHashMap::new);

    ComputeRuntimeDefaultMergingProcessor(MergingProcessor delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public void process(Node target, Node source, NodeProvider nodeProvider, NodeResolver nodeResolver) {
        stripComputeRuntimeDefaults(target, source);
        List<String> paths = computeProgramFieldPaths(source);
        preserveComputeFields(target, source, paths);
        delegate.process(target, source, nodeProvider, nodeResolver);
        suppressComputeFields(source, paths);
    }

    @Override
    public void postProcess(Node target, Node source, NodeProvider nodeProvider, NodeResolver nodeResolver) {
        stripComputeRuntimeDefaults(target, source);
        Map<Node, Map<String, Node>> suppressedByNode = suppressedComputeFields.get();
        Map<String, Node> suppressed = suppressedByNode.remove(source);
        try {
            delegate.postProcess(target, source, nodeProvider, nodeResolver);
        } finally {
            restoreComputeFields(source, suppressed);
            if (suppressedByNode.isEmpty()) {
                suppressedComputeFields.remove();
            }
        }
        preserveComputeFields(target, source, suppressed);
        preserveAuthoredMetadata(target, source);
    }

    private List<String> computeProgramFieldPaths(Node source) {
        if (!isComputeNode(source)) {
            return java.util.Collections.emptyList();
        }
        List<String> paths = new ArrayList<String>(4);
        addIfContainsBex(source, paths, "expr");
        addIfContainsBex(source, paths, "do");
        addIfContainsBex(source, paths, "constants");
        addIfContainsBex(source, paths, "functions");
        return paths;
    }

    private void addIfContainsBex(Node node, List<String> paths, String key) {
        Node value = property(node, key);
        if (containsBexOperator(value)) {
            paths.add("/" + key);
        }
    }

    private boolean containsBexOperator(Node node) {
        if (node == null) {
            return false;
        }
        Map<String, Node> properties = node.getProperties();
        if (properties != null) {
            if (properties.size() == 1) {
                String key = properties.keySet().iterator().next();
                if (key != null && key.startsWith("$")) {
                    return true;
                }
            }
            for (Node child : properties.values()) {
                if (containsBexOperator(child)) {
                    return true;
                }
            }
        }
        if (node.getItems() != null) {
            for (Node item : node.getItems()) {
                if (containsBexOperator(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void preserveComputeFields(Node target, Node source, List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        for (String path : paths) {
            Node preserved = NodePathAccessor.getNode(source, path);
            if (preserved != null) {
                NodePathEditor.put(target, path, preserved.clone());
            }
        }
    }

    private void preserveComputeFields(Node target, Node source, Map<String, Node> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Node> entry : fields.entrySet()) {
            NodePathEditor.put(target, "/" + entry.getKey(), entry.getValue().clone());
        }
    }

    private void suppressComputeFields(Node source, List<String> paths) {
        if (paths == null || paths.isEmpty() || source.getProperties() == null) {
            return;
        }
        Map<String, Node> suppressed = new LinkedHashMap<String, Node>();
        for (String path : paths) {
            String key = topLevelKey(path);
            if (key != null && source.getProperties().containsKey(key)) {
                suppressed.put(key, source.getProperties().remove(key));
            }
        }
        if (!suppressed.isEmpty()) {
            suppressedComputeFields.get().put(source, suppressed);
        }
    }

    private void restoreComputeFields(Node source, Map<String, Node> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        if (source.getProperties() == null) {
            source.properties(new LinkedHashMap<String, Node>());
        }
        source.getProperties().putAll(fields);
    }

    private String topLevelKey(String path) {
        if (path == null || path.length() < 2 || path.charAt(0) != '/') {
            return null;
        }
        String key = path.substring(1);
        return key.indexOf('/') >= 0 ? null : key;
    }

    private void preserveAuthoredMetadata(Node target, Node source) {
        if (source.getName() != null && target.getName() == null) {
            target.name(source.getName());
        }
        if (source.getDescription() != null && target.getDescription() == null) {
            target.description(source.getDescription());
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

    private boolean isComputeNode(Node node) {
        return hasTypeBlueId(node, Compute.blueId())
                || hasTypeBlueId(node, ComputeDefinition.blueId())
                || "Coordination/Compute".equals(typeValue(node))
                || "Coordination/Compute Definition".equals(typeValue(node));
    }

    private String typeValue(Node node) {
        if (node == null || node.getType() == null || node.getType().getValue() == null) {
            return null;
        }
        return String.valueOf(node.getType().getValue());
    }

    private Node property(Node node, String key) {
        return node != null && node.getProperties() != null ? node.getProperties().get(key) : null;
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
