package blue.contract.packager;

import blue.contract.packager.graphbuilder.DependencyGraphBuilder;
import blue.contract.packager.model.BluePackage;
import blue.contract.packager.model.DependencyGraph;
import blue.language.preprocess.Preprocessor;

import java.io.IOException;
import java.util.List;

public class BluePackageExporter {
    public static final String BOOTSTRAP_BLUE_ID = Preprocessor.DEFAULT_BLUE_BLUE_ID;
    public static final String ROOT_DEPENDENCY = "ROOT";
    public static final String BLUE_FILE_EXTENSION = ".blue";
    public static final String EXTENDS_FILE_NAME = "_extends.txt";
    public static final String REPLACE_INLINE_TYPES_WITH_BLUE_ID_TRANSFORMER_BLUE_ID = "27B7fuxQCS1VAptiCPc2RMkKoutP5qxkh3uDxZ7dr6Eo";

    public static List<BluePackage> exportPackages(String rootDir, DependencyGraphBuilder builder) throws IOException {
        DependencyGraph graph = builder.buildDependencyGraph(rootDir);
        DependencyProcessor processor = new DependencyProcessor(graph);
        return processor.process();
    }
}