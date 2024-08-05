package blue.contract.packager.utils;

import blue.language.model.Node;

import java.util.*;

public class NodeDependencyAnalyzer {
    public Map<String, Set<String>> analyzeDependencies(Map<String, Node> nodes) {
        Map<String, Set<String>> graph = new HashMap<>();
        for (Map.Entry<String, Node> entry : nodes.entrySet()) {
            String nodeName = entry.getKey();
            Node node = entry.getValue();
            Set<String> dependencies = findDependencies(node);
            dependencies.retainAll(nodes.keySet()); // Keep only dependencies within the same directory
            graph.put(nodeName, dependencies);
        }
        return graph;
    }

    private Set<String> findDependencies(Node node) {
        Set<String> dependencies = new HashSet<>();
        findDependenciesRecursive(node, dependencies);
        return dependencies;
    }

    private void findDependenciesRecursive(Node node, Set<String> dependencies) {
        if (node == null) {
            return;
        }

        addDependencyIfInline(node.getType(), dependencies);
        addDependencyIfInline(node.getItemType(), dependencies);
        addDependencyIfInline(node.getKeyType(), dependencies);
        addDependencyIfInline(node.getValueType(), dependencies);

        if (node.getItems() != null) {
            for (Node item : node.getItems()) {
                findDependenciesRecursive(item, dependencies);
            }
        }

        if (node.getProperties() != null) {
            for (Map.Entry<String, Node> entry : node.getProperties().entrySet()) {
                addDependencyIfInline(entry.getValue().getType(), dependencies);
                findDependenciesRecursive(entry.getValue(), dependencies);
            }
        }
    }

    private void addDependencyIfInline(Node typeNode, Set<String> dependencies) {
        if (typeNode != null && typeNode.isInlineValue()) {
            dependencies.add(typeNode.getValue().toString());
        }
    }
}