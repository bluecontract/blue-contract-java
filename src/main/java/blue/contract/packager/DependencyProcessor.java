package blue.contract.packager;

import blue.contract.packager.model.BluePackage;
import blue.contract.packager.model.DependencyGraph;
import blue.contract.packager.model.DirectoryNode;
import blue.contract.packager.utils.BluePackageInitializer;
import blue.contract.packager.utils.NodePreprocessor;
import blue.contract.packager.utils.PackageMappingsUpdater;
import blue.contract.packager.utils.TopologicalSorter;
import blue.language.model.Node;

import java.util.*;

class DependencyProcessor {
    private final DependencyGraph graph;
    private final Map<String, BluePackage> processedPackages;
    private final TopologicalSorter topologicalSorter;
    private final NodePreprocessor nodePreprocessor;
    private final BluePackageInitializer bluePackageInitializer;
    private final PackageMappingsUpdater packageMappingsUpdater;

    public DependencyProcessor(DependencyGraph graph) {
        this.graph = graph;
        this.processedPackages = new HashMap<>();
        this.topologicalSorter = new TopologicalSorter();
        this.nodePreprocessor = new NodePreprocessor();
        this.bluePackageInitializer = new BluePackageInitializer();
        this.packageMappingsUpdater = new PackageMappingsUpdater();
    }

    public List<BluePackage> process() {
        List<String> processingOrder = graph.getProcessingOrder();
        for (String dirName : processingOrder) {
            processDirectory(dirName);
        }
        return new ArrayList<>(processedPackages.values());
    }

    private void processDirectory(String dirName) {
        DirectoryNode dir = graph.getDirectories().get(dirName);
        BluePackage bluePackage = bluePackageInitializer.initialize(dirName, dir.getDependency(), processedPackages);
        processedPackages.put(dirName, bluePackage);

        Map<String, Node> nodes = new HashMap<>(dir.getNodes());
        Set<String> processed = new HashSet<>();
        List<String> processingOrder;
        try {
            processingOrder = topologicalSorter.sort(nodes);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Cyclic dependency detected in directory '" + dirName + "': " + e.getMessage());
        }

        for (String nodeName : processingOrder) {
            processNode(nodeName, nodes, processed, dirName);
        }
    }

    private void processNode(String nodeName, Map<String, Node> nodes, Set<String> processed, String dirName) {
        if (processed.contains(nodeName)) {
            return;
        }

        Node node = nodes.values().stream().filter(n -> n.getName().equals(nodeName)).findFirst().orElseThrow();
        Node preprocessedNode = nodePreprocessor.preprocess(node, dirName, processedPackages);

        nodes.put(nodeName, preprocessedNode);

        BluePackage bluePackage = processedPackages.get(dirName);
        bluePackage.addPreprocessedNode(nodeName, preprocessedNode);

        packageMappingsUpdater.update(dirName, nodeName, preprocessedNode, processedPackages);
        processed.add(nodeName);
    }
}