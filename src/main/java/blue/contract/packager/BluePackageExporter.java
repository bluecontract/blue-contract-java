package blue.contract.packager;

import blue.contract.packager.graphbuilder.DependencyGraphBuilder;
import blue.contract.packager.model.BluePackage;
import blue.contract.packager.model.DependencyGraph;

import java.io.IOException;
import java.util.List;

public class BluePackageExporter {
    public static final String BOOTSTRAP_BLUE_ID = "D5HTFSjSg2roM6mRnFkQqcQoA8YDeKWqcAH9sd4aBBnB";
    public static final String ROOT_DEPENDENCY = "ROOT";
    public static final String BLUE_FILE_EXTENSION = ".blue";
    public static final String EXTENDS_FILE_NAME = "_extends.txt";
    public static final String REPLACE_INLINE_TYPES_WITH_BLUE_ID_TRANSFORMER_BLUE_ID = "31WTDXxg5afqBNT8mpidmAxeuBs6SV1YXeaSrzjXzWdu";

    public static List<BluePackage> exportPackages(String rootDir, DependencyGraphBuilder builder) throws IOException {
        DependencyGraph graph = builder.buildDependencyGraph(rootDir);
        DependencyProcessor processor = new DependencyProcessor(graph);
        return processor.process();
    }
}