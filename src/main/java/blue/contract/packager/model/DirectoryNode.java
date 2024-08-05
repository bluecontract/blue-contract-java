package blue.contract.packager.model;

import blue.language.model.Node;

import java.util.HashMap;
import java.util.Map;

public class DirectoryNode {
    private String name;
    private String dependency;
    private Map<String, Node> nodes;

    public DirectoryNode(String name) {
        this.name = name;
        this.dependency = "";
        this.nodes = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public DirectoryNode setName(String name) {
        this.name = name;
        return this;
    }

    public String getDependency() {
        return dependency;
    }

    public DirectoryNode setDependency(String dependency) {
        this.dependency = dependency;
        return this;
    }

    public Map<String, Node> getNodes() {
        return nodes;
    }

    public DirectoryNode setNodes(Map<String, Node> nodes) {
        this.nodes = nodes;
        return this;
    }

    public void addNode(Node node) {
        String nodeName = node.getName();
        if (nodes.containsKey(nodeName)) {
            throw new IllegalArgumentException("A node with the name '" + nodeName + "' already exists in this directory.");
        }
        nodes.put(nodeName, node);
    }
}