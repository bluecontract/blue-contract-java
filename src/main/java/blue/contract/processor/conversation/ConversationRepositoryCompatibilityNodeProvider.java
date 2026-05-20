package blue.contract.processor.conversation;

import blue.language.NodeProvider;
import blue.language.model.Node;
import blue.language.provider.SequentialNodeProvider;
import blue.repo.conversation.Compute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConversationRepositoryCompatibilityNodeProvider implements NodeProvider {
    private final NodeProvider delegate;

    public ConversationRepositoryCompatibilityNodeProvider(NodeProvider delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    public static boolean isInstalled(NodeProvider provider) {
        if (provider instanceof ConversationRepositoryCompatibilityNodeProvider) {
            return true;
        }
        if (provider instanceof SequentialNodeProvider) {
            for (NodeProvider child : ((SequentialNodeProvider) provider).getNodeProviders()) {
                if (isInstalled(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<Node> fetchByBlueId(String blueId) {
        List<Node> nodes = delegate.fetchByBlueId(blueId);
        if (nodes == null || nodes.isEmpty() || !Compute.blueId().equals(baseBlueId(blueId))) {
            return nodes;
        }
        List<Node> compatible = new ArrayList<Node>(nodes.size());
        for (Node node : nodes) {
            compatible.add(sanitizeComputeDefinition(node));
        }
        return compatible;
    }

    private Node sanitizeComputeDefinition(Node node) {
        Node sanitized = node.clone();
        Map<String, Node> properties = sanitized.getProperties();
        if (properties == null || properties.isEmpty()) {
            return sanitized;
        }
        stripRuntimeDefault(properties, "emitEvents");
        stripRuntimeDefault(properties, "returnResult");
        return sanitized;
    }

    private void stripRuntimeDefault(Map<String, Node> properties, String key) {
        Node field = properties.get(key);
        if (field == null || !Boolean.TRUE.equals(field.getValue())) {
            return;
        }
        Node sanitized = field.clone();
        sanitized.value((Object) null);
        properties.put(key, sanitized);
    }

    private String baseBlueId(String blueId) {
        if (blueId == null) {
            return null;
        }
        int fragment = blueId.indexOf('#');
        return fragment >= 0 ? blueId.substring(0, fragment) : blueId;
    }
}
