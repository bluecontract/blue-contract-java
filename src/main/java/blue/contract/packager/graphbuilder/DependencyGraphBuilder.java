package blue.contract.packager.graphbuilder;

import blue.contract.packager.model.DependencyGraph;

import java.io.IOException;

public interface DependencyGraphBuilder {
    DependencyGraph buildDependencyGraph(String rootDir) throws IOException;
}