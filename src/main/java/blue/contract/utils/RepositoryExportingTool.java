package blue.contract.utils;

import blue.contract.packager.model.BluePackage;
import blue.contract.utils.PackagingUtils.ClasspathBasedPackagingEnvironment;
import blue.language.NodeProvider;
import blue.language.model.Node;
import blue.language.provider.ClasspathBasedNodeProvider;
import blue.language.utils.BlueIdCalculator;
import blue.language.utils.NodeToMapListOrValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static blue.contract.utils.PackagingUtils.createClasspathBasedPackagingEnvironment;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class RepositoryExportingTool {

    public static final String TARGET_DIR = "src/main/resources/blue-preprocessed";
    public static final String PACKAGE_BLUE = "package.blue";
    public static final String BLUE_IDS_YAML = "blue-ids.yaml";

    private final ClasspathBasedPackagingEnvironment env;

    public RepositoryExportingTool(ClasspathBasedPackagingEnvironment env) {
        this.env = env;
    }

    public static void main(String[] args) throws IOException {
        List<NodeProvider> additionalNodeProviders = Collections.singletonList(new ClasspathBasedNodeProvider("samples"));
        ClasspathBasedPackagingEnvironment env = createClasspathBasedPackagingEnvironment("blue-repository",
                "blue.contract.model", additionalNodeProviders);

        RepositoryExportingTool tool = new RepositoryExportingTool(env);
        tool.exportRepository();

        System.out.println("Repository export completed successfully.");
    }

    public void exportRepository() throws IOException {
        deleteAndRecreateTargetDir();

        Map<String, String> mainBlueIds = new HashMap<>();

        for (Map.Entry<String, BluePackage> entry : env.getPackageMap().entrySet()) {
            String packageName = entry.getKey();
            BluePackage bluePackage = entry.getValue();

            Path packageDir = Paths.get(TARGET_DIR, bluePackage.getDirectoryName());
            Files.createDirectories(packageDir);

            Node packageContent = bluePackage.getPackageContent();
            exportPackageContent(packageDir, packageContent);

            Map<String, String> packageBlueIds = createBlueIdsYaml(packageDir, packageContent);

            String packageBlueId = BlueIdCalculator.calculateBlueId(packageContent);
            mainBlueIds.put(bluePackage.getDirectoryName(), packageBlueId);

            for (Map.Entry<String, Node> nodeEntry : bluePackage.getPreprocessedNodes().entrySet()) {
                String nodeName = nodeEntry.getKey();
                Node node = nodeEntry.getValue();

                String yamlContent = YAML_MAPPER.writeValueAsString(NodeToMapListOrValue.get(node));

                String sanitizedNodeName = sanitizeName(nodeName);
                Path filePath = packageDir.resolve(sanitizedNodeName + ".blue");
                Files.writeString(filePath, yamlContent);
            }
        }

        createMainBlueIdsYaml(Paths.get(TARGET_DIR), mainBlueIds);
    }

    private void exportPackageContent(Path packageDir, Node packageContent) throws IOException {
        String yamlContent = YAML_MAPPER.writeValueAsString(NodeToMapListOrValue.get(packageContent));
        Path filePath = packageDir.resolve(PACKAGE_BLUE);
        Files.writeString(filePath, yamlContent);
    }

    private Map<String, String> createBlueIdsYaml(Path packageDir, Node packageContent) throws IOException {
        Map<String, String> blueIds = new HashMap<>();
        if (packageContent.getItems() != null && packageContent.getItems().size() > 1) {
            Node secondItem = packageContent.getItems().get(1);
            Node mappingsNode = secondItem.getProperties() != null ? secondItem.getProperties().get("mappings") : null;

            if (mappingsNode != null && mappingsNode.getProperties() != null) {
                for (Map.Entry<String, Node> mapping : mappingsNode.getProperties().entrySet()) {
                    String key = mapping.getKey();
                    String value = (String) mapping.getValue().getValue();
                    blueIds.put(key, value);
                }
            }
        }
        String yamlContent = YAML_MAPPER.writeValueAsString(blueIds);
        Path yamlPath = packageDir.resolve(BLUE_IDS_YAML);
        Files.writeString(yamlPath, yamlContent);
        return blueIds;
    }

    private void createMainBlueIdsYaml(Path targetDir, Map<String, String> blueIds) throws IOException {
        String yamlContent = YAML_MAPPER.writeValueAsString(blueIds);
        Path yamlPath = targetDir.resolve(BLUE_IDS_YAML);
        Files.writeString(yamlPath, yamlContent);
    }

    private String sanitizeName(String name) {
        return name.replaceAll("\\s+", "");
    }

    private void deleteAndRecreateTargetDir() throws IOException {
        Path targetPath = Paths.get(TARGET_DIR);
        if (Files.exists(targetPath)) {
            Files.walk(targetPath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
        Files.createDirectories(targetPath);
    }
}