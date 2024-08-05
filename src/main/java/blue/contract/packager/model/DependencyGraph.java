package blue.contract.packager.model;

import blue.language.model.Node;

import java.util.*;

import static blue.contract.packager.BluePackageExporter.ROOT_DEPENDENCY;

public class DependencyGraph {
    private Map<String, DirectoryNode> directories;

    public DependencyGraph() {
        this.directories = new HashMap<>();
    }

    public void addDirectory(String name, String dependency) {
        DirectoryNode node = new DirectoryNode(name);
        node.setDependency(dependency);
        directories.put(name, node);
    }

    public void addNode(String dirName, Node node) {
        directories.get(dirName).addNode(node);
    }

    public List<String> getProcessingOrder() {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String dirName : directories.keySet()) {
            if (hasCyclicDependency(dirName, visited, recursionStack, order)) {
                throw new IllegalStateException("Cyclic dependency detected in directory structure: " +
                                                String.join(" -> ", recursionStack) + " -> " + dirName);
            }
        }

        return order;
    }

    private boolean hasCyclicDependency(String dirName, Set<String> visited, Set<String> recursionStack, List<String> order) {
        if (recursionStack.contains(dirName)) {
            return true;
        }

        if (visited.contains(dirName)) {
            return false;
        }

        visited.add(dirName);
        recursionStack.add(dirName);

        DirectoryNode node = directories.get(dirName);
        String dep = node.getDependency();
        if (!dep.equals(ROOT_DEPENDENCY) && hasCyclicDependency(dep, visited, recursionStack, order)) {
            return true;
        }

        recursionStack.remove(dirName);
        order.add(dirName);
        return false;
    }

    public Map<String, DirectoryNode> getDirectories() {
        return directories;
    }

    public DependencyGraph setDirectories(Map<String, DirectoryNode> directories) {
        this.directories = directories;
        return this;
    }
}