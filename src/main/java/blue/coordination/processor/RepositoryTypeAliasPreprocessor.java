package blue.coordination.processor;

import blue.language.model.Node;
import blue.repo.BlueRepository;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RepositoryTypeAliasPreprocessor {
    private final Map<String, String> aliases;

    public RepositoryTypeAliasPreprocessor() {
        this(BlueRepository.latest());
    }

    public RepositoryTypeAliasPreprocessor(BlueRepository repository) {
        this(repository != null ? repository.typeAliases() : null);
    }

    public RepositoryTypeAliasPreprocessor(Map<String, String> aliases) {
        this.aliases = aliases != null
                ? new LinkedHashMap<String, String>(aliases)
                : new LinkedHashMap<String, String>();
    }

    public Node preprocess(Node node) {
        if (node == null) {
            return null;
        }
        Node copy = node.clone();
        resolve(copy);
        return copy;
    }

    private void resolve(Node node) {
        if (node == null) {
            return;
        }
        String blueId = aliasFor(node.getBlueId());
        if (blueId != null) {
            node.blueId(blueId);
        }

        node.type(resolveTypeNode(node.getType()));
        node.itemType(resolveTypeNode(node.getItemType()));
        node.keyType(resolveTypeNode(node.getKeyType()));
        node.valueType(resolveTypeNode(node.getValueType()));

        if (node.getItems() != null) {
            for (Node item : node.getItems()) {
                resolve(item);
            }
        }
        if (node.getProperties() != null) {
            for (Node value : node.getProperties().values()) {
                resolve(value);
            }
        }
        resolve(node.getContracts());
        resolve(node.getBlue());
    }

    private Node resolveTypeNode(Node typeNode) {
        if (typeNode == null) {
            return null;
        }
        String blueId = aliasFor(inlineText(typeNode));
        if (blueId != null) {
            return new Node().blueId(blueId);
        }
        resolve(typeNode);
        return typeNode;
    }

    private String inlineText(Node node) {
        if (node == null || !node.isInlineValue() || node.getValue() == null) {
            return null;
        }
        return String.valueOf(node.getValue());
    }

    private String aliasFor(String value) {
        if (value == null) {
            return null;
        }
        return aliases.get(value);
    }

}
