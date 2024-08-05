package blue.contract.packager.graphbuilder;

import blue.contract.packager.model.DependencyGraph;
import blue.language.model.Node;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static blue.contract.packager.BluePackageExporter.EXTENDS_FILE_NAME;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class FileSystemDependencyGraphBuilder implements DependencyGraphBuilder {
    private final Path rootPath;

    public FileSystemDependencyGraphBuilder(Path rootPath) {
        this.rootPath = rootPath;
    }

    @Override
    public DependencyGraph buildDependencyGraph(String rootDir) throws IOException {
        DependencyGraph graph = new DependencyGraph();
        Path fullPath = rootPath.resolve(rootDir);

        if (!Files.exists(fullPath)) {
            throw new IOException("Root directory not found: " + fullPath);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(fullPath)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    String dirName = path.getFileName().toString();
                    String dependency = readDependency(path.resolve(EXTENDS_FILE_NAME));
                    graph.addDirectory(dirName, dependency);
                    processFiles(path, dirName, graph);
                }
            }
        }

        return graph;
    }

    private String readDependency(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Dependency file not found: " + path);
        }
        String content = Files.readString(path).trim();
        if (content.isEmpty() || content.lines().count() != 1) {
            throw new IOException("Invalid dependency file format. Expected one line in: " + path);
        }
        return content;
    }

    private void processFiles(Path dirPath, String dirName, DependencyGraph graph) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "*.blue")) {
            for (Path file : stream) {
                Node contract = YAML_MAPPER.readValue(Files.readString(file), Node.class);
                graph.addNode(dirName, contract);
            }
        }
    }

}