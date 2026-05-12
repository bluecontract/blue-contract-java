package blue.contract.processor.conversation.javascript;

import blue.language.model.Node;
import blue.language.utils.NodeToMapListOrValue;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JavaScriptValues {
    private static final ObjectMapper JSON_WITH_NULLS = new ObjectMapper(new JsonFactory())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);

    private JavaScriptValues() {
    }

    public static Node toNode(Object value) {
        if (value == null) {
            return new Node();
        }
        try {
            String json = JSON_WITH_NULLS.writeValueAsString(value);
            return JSON_WITH_NULLS.readValue(json, Node.class);
        } catch (IOException ex) {
            throw new JavaScriptExecutionException("Failed to convert JavaScript value to Blue node", ex);
        }
    }

    public static Object simple(Node node) {
        return node == null ? null : NodeToMapListOrValue.get(node, NodeToMapListOrValue.Strategy.SIMPLE);
    }

    public static Object official(Node node) {
        return node == null ? null : NodeToMapListOrValue.get(node, NodeToMapListOrValue.Strategy.OFFICIAL);
    }

    public static Map<String, Object> stepResults(Map<String, Object> results) {
        Map<String, Object> normalized = new LinkedHashMap<String, Object>();
        if (results == null) {
            return normalized;
        }
        for (Map.Entry<String, Object> entry : results.entrySet()) {
            Object result = entry.getValue();
            if (result instanceof Node) {
                normalized.put(entry.getKey(), simple((Node) result));
            } else {
                normalized.put(entry.getKey(), result);
            }
        }
        return normalized;
    }

    public static Map<String, Object> metadataIndex(Node node) {
        Map<String, Object> index = new LinkedHashMap<String, Object>();
        indexMetadata(index, "/", node);
        return index;
    }

    private static void indexMetadata(Map<String, Object> index, String pointer, Node node) {
        if (node == null) {
            return;
        }
        if (node.getName() != null) {
            index.put(append(pointer, "name"), node.getName());
        }
        if (node.getDescription() != null) {
            index.put(append(pointer, "description"), node.getDescription());
        }
        if (node.getValue() != null) {
            index.put(append(pointer, "value"), node.getValue());
        }
        if (node.getType() != null) {
            indexMetadata(index, append(pointer, "type"), node.getType());
        }
        if (node.getProperties() != null) {
            for (Map.Entry<String, Node> entry : node.getProperties().entrySet()) {
                indexMetadata(index, append(pointer, entry.getKey()), entry.getValue());
            }
        }
        List<Node> items = node.getItems();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                indexMetadata(index, append(pointer, Integer.toString(i)), items.get(i));
            }
        }
    }

    private static String append(String parent, String segment) {
        String escaped = segment.replace("~", "~0").replace("/", "~1");
        if (parent == null || "/".equals(parent)) {
            return "/" + escaped;
        }
        return parent + "/" + escaped;
    }
}
