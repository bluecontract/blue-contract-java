package blue.contract.packager;

import blue.contract.packager.graphbuilder.ClasspathDependencyGraphBuilder;
import blue.contract.packager.graphbuilder.DependencyGraphBuilder;
import blue.contract.packager.graphbuilder.FileSystemDependencyGraphBuilder;
import blue.contract.packager.graphbuilder.SequentialDependencyGraphBuilder;
import blue.contract.packager.model.BluePackage;
import blue.language.model.Node;
import blue.language.utils.BlueIdCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BluePackageExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void testExportPackages() throws IOException {
        Path contentDir = tempDir.resolve("content");
        Path abcDir = contentDir.resolve("abc");
        Path cdeDir = contentDir.resolve("cde");
        Files.createDirectories(abcDir);
        Files.createDirectories(cdeDir);

        Files.writeString(abcDir.resolve("_extends.txt"), "ROOT");
        Files.writeString(cdeDir.resolve("_extends.txt"), "abc");

        String aContent = "name: A\n" +
                          "type: B";
        String bContent = "name: B\n" +
                          "type: A";
        Files.writeString(abcDir.resolve("A.blue"), aContent);
        Files.writeString(abcDir.resolve("B.blue"), bContent);

        String xContent = "name: X\n" +
                          "a:\n" +
                          "  type: A";
        Files.writeString(cdeDir.resolve("X.blue"), xContent);

        DependencyGraphBuilder builder = new FileSystemDependencyGraphBuilder(tempDir);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            BluePackageExporter.exportPackages("content", builder);
        });

        String expectedMessage = "Cyclic dependency detected in directory 'abc': Cyclic dependency detected: A -> B -> A";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage),
                "Expected exception message to contain '" + expectedMessage + "' but was '" + actualMessage + "'");
    }

    @Test
    void testExportPackagesWithoutCyclicDependency() throws IOException {
        Path contentDir = tempDir.resolve("content");
        Path abcDir = contentDir.resolve("abc");
        Path cdeDir = contentDir.resolve("cde");
        Files.createDirectories(abcDir);
        Files.createDirectories(cdeDir);

        Files.writeString(abcDir.resolve("_extends.txt"), "ROOT");
        Files.writeString(cdeDir.resolve("_extends.txt"), "abc");

        String aContent = "name: A\n" +
                          "type: String";
        String bContent = "name: B\n" +
                          "type: A";
        String cContent = "name: C\n" +
                          "x:\n" +
                          "  type: D";
        String dContent = "name: D\n" +
                          "y:\n" +
                          "  type: List\n" +
                          "  itemType: A";
        Files.writeString(abcDir.resolve("A.blue"), aContent);
        Files.writeString(abcDir.resolve("B.blue"), bContent);
        Files.writeString(abcDir.resolve("C.blue"), cContent);
        Files.writeString(abcDir.resolve("D.blue"), dContent);

        String xContent = "name: X\n" +
                          "a:\n" +
                          "  type: A\n" +
                          "b:\n" +
                          "  type: B";
        Files.writeString(cdeDir.resolve("X.blue"), xContent);

        DependencyGraphBuilder builder = new FileSystemDependencyGraphBuilder(tempDir);

        List<BluePackage> packages = BluePackageExporter.exportPackages("content", builder);

        assertNotNull(packages);
        assertEquals(2, packages.size());
        assertEquals("abc", packages.get(0).getDirectoryName());
        assertEquals("cde", packages.get(1).getDirectoryName());

        // Assert abc package structure
        BluePackage abcPackage = packages.get(0);
        Node abcPackageContent = abcPackage.getPackageContent();
        assertEquals(2, abcPackageContent.getItems().size());

        // First item should have BOOTSTRAP_BLUE_ID
        assertEquals(BluePackageExporter.BOOTSTRAP_BLUE_ID, abcPackageContent.getItems().get(0).getBlueId());

        // Second item should have REPLACE_INLINE_TYPES_WITH_BLUE_ID_TRANSFORMER_BLUE_ID as its type
        Node abcTypeNode = abcPackageContent.getItems().get(1);
        assertEquals(BluePackageExporter.REPLACE_INLINE_TYPES_WITH_BLUE_ID_TRANSFORMER_BLUE_ID, abcTypeNode.getType().getBlueId());

        // Check mappings in abc package
        Node abcMappingsNode = abcTypeNode.getProperties().get("mappings");
        assertNotNull(abcMappingsNode);
        assertEquals(4, abcMappingsNode.getProperties().size());

        // Get preprocessed nodes
        Map<String, Node> preprocessedNodes = abcPackage.getPreprocessedNodes();

        // Check individual nodes in abc package
        Node nodeA = abcMappingsNode.getProperties().get("A");
        Node nodeB = abcMappingsNode.getProperties().get("B");
        Node nodeC = abcMappingsNode.getProperties().get("C");
        Node nodeD = abcMappingsNode.getProperties().get("D");

        // Calculate expected Blue IDs using preprocessed nodes
        String expectedABlueId = BlueIdCalculator.calculateBlueId(preprocessedNodes.get("A"));
        String expectedBBlueId = BlueIdCalculator.calculateBlueId(preprocessedNodes.get("B"));
        String expectedCBlueId = BlueIdCalculator.calculateBlueId(preprocessedNodes.get("C"));
        String expectedDBlueId = BlueIdCalculator.calculateBlueId(preprocessedNodes.get("D"));

        // Assert calculated Blue IDs match the ones in the package
        assertEquals(expectedABlueId, nodeA.getValue());
        assertEquals(expectedBBlueId, nodeB.getValue());
        assertEquals(expectedCBlueId, nodeC.getValue());
        assertEquals(expectedDBlueId, nodeD.getValue());

        // Assert cde package structure
        BluePackage cdePackage = packages.get(1);
        Node cdePackageContent = cdePackage.getPackageContent();
        assertEquals(2, cdePackageContent.getItems().size());

        // First item should have blueId calculated from abc package
        String abcPackageBlueId = BlueIdCalculator.calculateBlueId(abcPackageContent.getItems());
        assertEquals(abcPackageBlueId, cdePackageContent.getItems().get(0).getBlueId());

        // Second item should have TYPE_BLUE_ID as its type
        Node cdeTypeNode = cdePackageContent.getItems().get(1);
        assertEquals(BluePackageExporter.REPLACE_INLINE_TYPES_WITH_BLUE_ID_TRANSFORMER_BLUE_ID, cdeTypeNode.getType().getBlueId());

        // Check mappings in cde package
        Node cdeMappingsNode = cdeTypeNode.getProperties().get("mappings");
        assertNotNull(cdeMappingsNode);
        assertEquals(1, cdeMappingsNode.getProperties().size());

        // Check X node in cde package
        Node nodeX = cdeMappingsNode.getProperties().get("X");
        assertNotNull(nodeX);

        // Calculate expected Blue ID for X using preprocessed node
        String expectedXBlueId = BlueIdCalculator.calculateBlueId(cdePackage.getPreprocessedNodes().get("X"));
        assertEquals(expectedXBlueId, nodeX.getValue());

        // Verify that B's type references A's blueId
        Node reconstructedB = new Node()
                .name("B")
                .type(new Node().blueId((String) nodeA.getValue()));
        assertEquals(nodeB.getValue(), BlueIdCalculator.calculateBlueId(reconstructedB));

        // Verify that C's x property type references D's blueId
        Node reconstructedC = new Node()
                .name("C")
                .properties(Map.of("x", new Node().type(new Node().blueId((String) nodeD.getValue()))));
        assertEquals(nodeC.getValue(), BlueIdCalculator.calculateBlueId(reconstructedC));

        // Verify that D's y property itemType references A's blueId
        Node reconstructedD = new Node()
                .name("D")
                .properties(Map.of("y", new Node()
                        .type(new Node().blueId("G8wmfjEqugPEEXByMYWJXiEdbLToPRWNQEekNxrxfQWB"))
                        .itemType(new Node().blueId((String) nodeA.getValue()))));
        assertEquals(nodeD.getValue(), BlueIdCalculator.calculateBlueId(reconstructedD));

        // Verify that X's properties reference A and B's blueIds
        Node reconstructedX = new Node()
                .name("X")
                .properties(Map.of(
                        "a", new Node().type(new Node().blueId((String) nodeA.getValue())),
                        "b", new Node().type(new Node().blueId((String) nodeB.getValue()))
                ));
        assertEquals(nodeX.getValue(), BlueIdCalculator.calculateBlueId(reconstructedX));
    }

    @Test
    void testExportPackagesFromClasspath() throws IOException {
        DependencyGraphBuilder builder = new ClasspathDependencyGraphBuilder(getClass().getClassLoader());

        List<BluePackage> packages = BluePackageExporter.exportPackages("content", builder);

        assertNotNull(packages);
        assertEquals(2, packages.size());
        assertEquals("abc", packages.get(0).getDirectoryName());
        assertEquals("cde", packages.get(1).getDirectoryName());
    }

    @Test
    void testExportPackagesWithSequentialBuilder() throws IOException {
        Path contentDir = tempDir.resolve("content");
        Path mnpDir = contentDir.resolve("mnp");
        Path xyzDir = contentDir.resolve("xyz");
        Files.createDirectories(mnpDir);
        Files.createDirectories(xyzDir);

        Files.writeString(mnpDir.resolve("_extends.txt"), "ROOT");
        Files.writeString(xyzDir.resolve("_extends.txt"), "mnp");

        String mContent = "name: M\n" +
                          "type: String";
        String nContent = "name: N\n" +
                          "type: M";
        Files.writeString(mnpDir.resolve("M.blue"), mContent);
        Files.writeString(mnpDir.resolve("N.blue"), nContent);

        String xContent = "name: X\n" +
                          "m:\n" +
                          "  type: M\n" +
                          "n:\n" +
                          "  type: N";
        Files.writeString(xyzDir.resolve("X.blue"), xContent);

        FileSystemDependencyGraphBuilder fsBuilder = new FileSystemDependencyGraphBuilder(tempDir);
        ClasspathDependencyGraphBuilder cpBuilder = new ClasspathDependencyGraphBuilder(getClass().getClassLoader());

        SequentialDependencyGraphBuilder sequentialBuilder = new SequentialDependencyGraphBuilder(
                Arrays.asList(fsBuilder, cpBuilder)
        );

        List<BluePackage> packages = BluePackageExporter.exportPackages("content", sequentialBuilder);

        assertNotNull(packages);
        assertEquals(4, packages.size());

        List<String> packageNames = packages.stream()
                .map(BluePackage::getDirectoryName)
                .toList();
        assertTrue(packageNames.contains("mnp"));
        assertTrue(packageNames.contains("xyz"));

    }


}