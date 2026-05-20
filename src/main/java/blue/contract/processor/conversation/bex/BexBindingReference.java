package blue.contract.processor.conversation.bex;

import blue.language.model.Node;
import blue.language.snapshot.FrozenNode;

import java.util.Map;

public final class BexBindingReference {
    private final String name;
    private final String path;

    private BexBindingReference(String name, String path) {
        this.name = name;
        this.path = path != null && !path.trim().isEmpty() ? path.trim() : "/";
    }

    public String name() {
        return name;
    }

    public String path() {
        return path;
    }

    public static BexBindingReference parse(Node node) {
        if (node == null || node.getProperties() == null || node.getProperties().size() != 1) {
            return null;
        }
        Node body = node.getProperties().get("$binding");
        if (body == null || body.getProperties() == null) {
            return null;
        }
        String name = body.getName() != null ? body.getName() : text(body.getProperties().get("name"));
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String path = text(body.getProperties().get("path"));
        return new BexBindingReference(name.trim(), path);
    }

    public static BexBindingReference parse(FrozenNode node) {
        if (node == null || node.getProperties() == null || node.getProperties().size() != 1) {
            return null;
        }
        FrozenNode body = node.getProperties().get("$binding");
        if (body == null || body.getProperties() == null) {
            return null;
        }
        Map<String, FrozenNode> properties = body.getProperties();
        String name = body.getName() != null ? body.getName() : text(properties.get("name"));
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String path = text(properties.get("path"));
        return new BexBindingReference(name.trim(), path);
    }

    private static String text(Node node) {
        if (node == null) {
            return null;
        }
        if (node.getValue() != null) {
            return String.valueOf(node.getValue());
        }
        if (node.getProperties() != null && node.getProperties().containsKey("value")) {
            return text(node.getProperties().get("value"));
        }
        return null;
    }

    private static String text(FrozenNode node) {
        if (node == null) {
            return null;
        }
        if (node.getValue() != null) {
            return String.valueOf(node.getValue());
        }
        if (node.getProperties() != null && node.getProperties().containsKey("value")) {
            return text(node.getProperties().get("value"));
        }
        return null;
    }
}
