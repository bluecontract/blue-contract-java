package blue.contract.processor.conversation;

import blue.language.Blue;
import blue.language.NodeProvider;
import blue.language.model.Node;
import blue.language.processor.registry.BlueRuntimeTypeRegistry;
import blue.language.provider.BootstrapProvider;
import blue.language.provider.SequentialNodeProvider;
import blue.language.utils.NodeProviderWrapper;
import blue.repo.BlueRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ConversationTestResources {
    private ConversationTestResources() {
    }

    public static String readResource(String resourcePath) {
        String normalizedPath = normalizeResourcePath(resourcePath);
        InputStream stream = ConversationTestResources.class.getClassLoader()
                .getResourceAsStream(normalizedPath);
        if (stream == null) {
            throw new IllegalArgumentException("Missing test resource: " + resourcePath);
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read test resource: " + resourcePath, ex);
        }
    }

    public static Node yamlResource(Blue blue, BlueRepository repository, String resourcePath) {
        Node node = blue.parseSourceYaml(readResource(resourcePath));
        node.blue(repository.typeAliasBlue());
        return blue.preprocess(node);
    }

    public static Blue configuredBlue(BlueRepository repository) {
        Blue blue = repository.configure(new Blue());
        NodeProvider provider = new SequentialNodeProvider(Arrays.asList(
                BootstrapProvider.INSTANCE,
                BlueRuntimeTypeRegistry.getDefault().asProvider(),
                NodeProviderWrapper.unverified(repository.nodeProvider())));
        blue.nodeProvider(provider);
        return blue;
    }

    public static String simpleTimelineChannelYaml(String key, String timelineId, int indent) {
        String base = spaces(indent);
        String child = spaces(indent + 2);
        String grandchild = spaces(indent + 4);
        return String.join("\n",
                base + key + ":",
                child + "type:",
                grandchild + "blueId: " + TestTimelineProvider.SIMPLE_TIMELINE_CHANNEL_BLUE_ID,
                child + "timelineId: " + timelineId);
    }

    public static Node operationRequest(String operation, Node request) {
        Node safeRequest = request != null ? request : new Node();
        return new Node()
                .type("Conversation/Operation Request")
                .properties("operation", new Node().value(operation))
                .properties("request", safeRequest);
    }

    public static Node operationRequestEvent(Blue blue,
                                             BlueRepository repository,
                                             String timelineId,
                                             int timestamp,
                                             String operation,
                                             Node request) {
        return TestTimelineProvider.timelineEntry(blue,
                repository,
                timelineId,
                timestamp,
                operationRequest(operation, request));
    }

    private static String normalizeResourcePath(String resourcePath) {
        if (resourcePath == null) {
            throw new IllegalArgumentException("resourcePath must not be null");
        }
        return resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
    }

    private static String spaces(int count) {
        if (count <= 0) {
            return "";
        }
        char[] chars = new char[count];
        Arrays.fill(chars, ' ');
        return new String(chars);
    }
}
