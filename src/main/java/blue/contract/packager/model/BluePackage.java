package blue.contract.packager.model;

import blue.language.model.Node;

import java.util.HashMap;
import java.util.Map;

public class BluePackage {
    private final String directoryName;
    private final Node packageContent;
    private Map<String, String> mappings;
    private Map<String, Node> preprocessedNodes;

    public BluePackage(String directoryName, Node packageContent) {
        this.directoryName = directoryName;
        this.packageContent = packageContent;
        this.mappings = new HashMap<>();
        this.preprocessedNodes = new HashMap<>();

    }

    public String getDirectoryName() {
        return directoryName;
    }

    public Node getPackageContent() {
        return packageContent;
    }

    public Map<String, String> getMappings() {
        return new HashMap<>(mappings);
    }
    
    public Map<String, Node> getPreprocessedNodes() {
        return new HashMap<>(preprocessedNodes);
    }

    public void addPreprocessedNode(String nodeName, Node node) {
        this.preprocessedNodes.put(nodeName, node);
    }
}