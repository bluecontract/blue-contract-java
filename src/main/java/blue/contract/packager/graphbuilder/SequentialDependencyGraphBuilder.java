package blue.contract.packager.graphbuilder;

import blue.contract.packager.model.DependencyGraph;
import blue.contract.packager.model.DirectoryNode;
import blue.language.model.Node;

import java.io.IOException;
import java.util.List;

public class SequentialDependencyGraphBuilder implements DependencyGraphBuilder {
    private final List<DependencyGraphBuilder> builders;

    public SequentialDependencyGraphBuilder(List<DependencyGraphBuilder> builders) {
        this.builders = builders;
    }

    @Override
    public DependencyGraph buildDependencyGraph(String rootDir) throws IOException {
        DependencyGraph combinedGraph = new DependencyGraph();

        for (DependencyGraphBuilder builder : builders) {
            DependencyGraph graph = builder.buildDependencyGraph(rootDir);
            mergeGraphs(combinedGraph, graph);
        }

        return combinedGraph;
    }

    private void mergeGraphs(DependencyGraph target, DependencyGraph source) {
        for (String dirName : source.getDirectories().keySet()) {
            DirectoryNode sourceDir = source.getDirectories().get(dirName);
            DirectoryNode targetDir = target.getDirectories().get(dirName);

            if (targetDir != null) {
                throw new IllegalStateException("Directory name collision detected: " + dirName);
            }

            target.addDirectory(dirName, sourceDir.getDependency());
            for (Node node : sourceDir.getNodes().values()) {
                target.addNode(dirName, node);
            }
        }
    }
}