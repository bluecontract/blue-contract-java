package blue.contract.utils;

import blue.contract.simulator.model.SimulatorTimelineEntry;
import blue.language.Blue;
import blue.language.NodeProvider;
import blue.language.model.Node;
import blue.language.provider.ClasspathBasedNodeProvider;
import blue.language.provider.DirectoryBasedNodeProvider;
import blue.language.utils.BlueIds;
import blue.language.utils.TypeClassResolver;
import blue.language.utils.UncheckedObjectMapper;
import blue.language.utils.limits.TypeSpecificPropertyFilter;
import org.gradle.internal.impldep.com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static blue.contract.utils.PackagingUtils.createClasspathBasedPackagingEnvironment;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class Utils {

    public static final String TESTING_EVENTS = "Testing Events";
    public static final String TESTING_FLOWS = "Testing Flows";
    public static final String TESTING_LOCAL_SUBSCRIPTIONS = "Testing Local Subscriptions";

    public static final String SAMPLES_DIR = "src/main/resources/samples";
    public static final String REPOSITORY_DIR = "blue-repository";
    private static final String PREPROCESSED_DIR = "src/main/resources/blue-preprocessed";

    public static PackagingUtils.ClasspathBasedPackagingEnvironment defaultTestingEnvironment() throws IOException {
        List<NodeProvider> additionalNodeProviders = Collections.singletonList(new ClasspathBasedNodeProvider(SAMPLES_DIR));
        return createClasspathBasedPackagingEnvironment(REPOSITORY_DIR, "blue.contract.model", additionalNodeProviders);
    }

    public static Blue testBlue() {
        try {
            Blue blue = new Blue(
                    new DirectoryBasedNodeProvider(PREPROCESSED_DIR, SAMPLES_DIR),
                    new TypeClassResolver("blue.contract.model")
            );
            blue.setGlobalLimits(new TypeSpecificPropertyFilter(
                    BlueIds.getBlueId(SimulatorTimelineEntry.class).orElseThrow(() -> new RuntimeException("No Simulator Timeline Entry blueId found.")),
                    ImmutableSet.of("timeline", "timelinePrev", "thread", "threadPrev", "signature")));
            return blue;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Node getPreprocessedNode(String dir, String name) throws IOException {
        Path dirPath = Paths.get(PREPROCESSED_DIR, dir);
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            throw new IOException("Directory does not exist or is not a directory: " + dirPath);
        }

        try (Stream<Path> paths = Files.walk(dirPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".blue"))
                    .map(path -> {
                        try {
                            Node node = YAML_MAPPER.readValue(path.toFile(), Node.class);
                            if (name.equals(node.getName())) {
                                return node;
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Error reading file: " + path + ". " + e.getMessage());
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No file found in dir " + dir + " containing name \"" + name + "\""));
        }
    }
}