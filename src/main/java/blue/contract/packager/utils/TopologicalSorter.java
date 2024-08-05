package blue.contract.packager.utils;

import blue.language.model.Node;

import java.util.*;

public class TopologicalSorter {
    private final NodeDependencyAnalyzer dependencyAnalyzer;

    public TopologicalSorter() {
        this.dependencyAnalyzer = new NodeDependencyAnalyzer();
    }

    public List<String> sort(Map<String, Node> nodes) {
        Map<String, Set<String>> graph = dependencyAnalyzer.analyzeDependencies(nodes);
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> temp = new HashSet<>();
        List<String> path = new ArrayList<>();

        for (String node : nodes.keySet()) {
            if (!visited.contains(node)) {
                dfs(node, graph, visited, temp, result, path);
            }
        }

        return result;
    }

    private void dfs(String node, Map<String, Set<String>> graph, Set<String> visited, Set<String> temp, List<String> result, List<String> path) {
        if (temp.contains(node)) {
            int startIndex = path.indexOf(node);
            List<String> cycle = path.subList(startIndex, path.size());
            cycle.add(node);
            throw new IllegalStateException("Cyclic dependency detected: " + String.join(" -> ", cycle));
        }
        if (!visited.contains(node)) {
            temp.add(node);
            path.add(node);
            Set<String> dependencies = graph.getOrDefault(node, Collections.emptySet());
            for (String dep : dependencies) {
                dfs(dep, graph, visited, temp, result, path);
            }
            visited.add(node);
            temp.remove(node);
            path.remove(path.size() - 1);
            result.add(node);
        }
    }
}