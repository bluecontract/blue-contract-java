package blue.contract.packager.graphbuilder;

import blue.contract.packager.model.DependencyGraph;
import blue.language.model.Node;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;

import static blue.contract.packager.BluePackageExporter.BLUE_FILE_EXTENSION;
import static blue.contract.packager.BluePackageExporter.EXTENDS_FILE_NAME;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class ClasspathDependencyGraphBuilder implements DependencyGraphBuilder {
    private final ClassLoader classLoader;

    public ClasspathDependencyGraphBuilder(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public DependencyGraph buildDependencyGraph(String rootDir) throws IOException {
        DependencyGraph graph = new DependencyGraph();
        URL resource = classLoader.getResource(rootDir);

        if (resource == null) {
            throw new IOException("Root directory not found in classpath: " + rootDir);
        }

        try (InputStream is = resource.openStream()) {
            Scanner scanner = new Scanner(is).useDelimiter("\\A");
            String content = scanner.hasNext() ? scanner.next() : "";
            String[] directories = content.split("\n");

            for (String dir : directories) {
                String dependency = readDependency(rootDir + "/" + dir + "/_extends.txt");
                graph.addDirectory(dir, dependency);
                processFiles(rootDir, dir, graph);
            }
        }

        return graph;
    }

    private String readDependency(String path) throws IOException {
        try (InputStream is = classLoader.getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Dependency file not found: " + path);
            }
            Scanner scanner = new Scanner(is).useDelimiter("\\A");
            String content = scanner.hasNext() ? scanner.next().trim() : "";
            if (content.isEmpty() || content.lines().count() != 1) {
                throw new IOException("Invalid dependency file format. Expected one line in: " + path);
            }
            return content;
        }
    }

    private void processFiles(String rootDir, String dirName, DependencyGraph graph) throws IOException {
        String dirPath = rootDir + "/" + dirName + "/";
        URL dirUrl = classLoader.getResource(dirPath);
        if (dirUrl == null) {
            return;
        }

        try (InputStream is = dirUrl.openStream()) {
            Scanner scanner = new Scanner(is).useDelimiter("\\A");
            String content = scanner.hasNext() ? scanner.next() : "";
            String[] files = content.split("\n");

            for (String file : files) {
                if (!file.equals(EXTENDS_FILE_NAME) && file.endsWith(BLUE_FILE_EXTENSION)) {
                    String filePath = dirPath + file;
                    try (InputStream fileIs = classLoader.getResourceAsStream(filePath)) {
                        if (fileIs != null) {
                            Node contract = YAML_MAPPER.readValue(fileIs, Node.class);
                            graph.addNode(dirName, contract);
                        }
                    }
                }
            }
        }
    }
}