package blue.contract.processor.conversation.workflow;

import blue.language.model.Node;

import java.util.LinkedHashMap;
import java.util.Map;

final class ComputeProgramNormalizer {
    Node program(Node stepNode) {
        Node program = new Node();
        copyMetadata(program, stepNode);
        Map<String, Node> properties = new LinkedHashMap<String, Node>();
        putIfMeaningful(properties, "expr", NodeUtil.property(stepNode, "expr"));
        putIfMeaningful(properties, "do", normalizeDo(NodeUtil.property(stepNode, "do")));
        putIfMeaningful(properties, "entry", NodeUtil.property(stepNode, "entry"));
        putIfMeaningful(properties, "constants", authoredMap(NodeUtil.property(stepNode, "constants")));
        putIfMeaningful(properties, "functions", normalizeFunctions(NodeUtil.property(stepNode, "functions")));
        putIfMeaningful(properties, "gasLimit", NodeUtil.property(stepNode, "gasLimit"));
        putIfMeaningful(properties, "emitEvents", NodeUtil.property(stepNode, "emitEvents"));
        putIfMeaningful(properties, "returnResult", NodeUtil.property(stepNode, "returnResult"));
        if (!properties.isEmpty()) {
            program.properties(properties);
        }
        return program;
    }

    Node definition(Node definitionNode) {
        Node definition = new Node();
        copyMetadata(definition, definitionNode);
        Map<String, Node> properties = new LinkedHashMap<String, Node>();
        putIfMeaningful(properties, "constants", authoredMap(NodeUtil.property(definitionNode, "constants")));
        putIfMeaningful(properties, "functions", normalizeFunctions(NodeUtil.property(definitionNode, "functions")));
        if (!properties.isEmpty()) {
            definition.properties(properties);
        }
        return definition;
    }

    private Node normalizeFunctions(Node functions) {
        if (functions == null || functions.getProperties() == null || functions.getProperties().isEmpty()) {
            return null;
        }
        Map<String, Node> normalized = new LinkedHashMap<String, Node>();
        for (Map.Entry<String, Node> entry : functions.getProperties().entrySet()) {
            normalized.put(entry.getKey(), normalizeFunction(entry.getValue()));
        }
        return new Node().properties(normalized);
    }

    private Node normalizeFunction(Node function) {
        if (function == null || function.getProperties() == null) {
            return function != null ? function.clone() : new Node();
        }
        Map<String, Node> properties = new LinkedHashMap<String, Node>();
        putIfMeaningful(properties, "args", authoredMap(NodeUtil.property(function, "args")));
        putIfMeaningful(properties, "expr", NodeUtil.property(function, "expr"));
        putIfMeaningful(properties, "do", normalizeDo(NodeUtil.property(function, "do")));
        return new Node().properties(properties);
    }

    private Node normalizeDo(Node doNode) {
        if (doNode == null || doNode.getItems() == null || doNode.getItems().isEmpty()) {
            return null;
        }
        java.util.List<Node> items = new java.util.ArrayList<Node>();
        for (Node item : doNode.getItems()) {
            items.add(normalizeStatement(item));
        }
        return new Node().items(items);
    }

    private Node normalizeStatement(Node statement) {
        if (NodeUtil.isEmpty(statement)) {
            return new Node().properties("$return", new Node());
        }
        return statement.clone();
    }

    private Node authoredMap(Node node) {
        if (node == null || node.getProperties() == null || node.getProperties().isEmpty()) {
            return null;
        }
        return node.clone();
    }

    private void putIfMeaningful(Map<String, Node> properties, String key, Node value) {
        if (hasAuthoredContent(value)) {
            properties.put(key, value.clone());
        }
    }

    private boolean hasAuthoredContent(Node node) {
        return node != null
                && (node.getValue() != null
                || (node.getItems() != null && !node.getItems().isEmpty())
                || (node.getProperties() != null && !node.getProperties().isEmpty()));
    }

    private void copyMetadata(Node target, Node source) {
        if (source == null) {
            return;
        }
        target.name(source.getName());
        target.description(source.getDescription());
        target.type(source.getType() != null ? source.getType().clone() : null);
    }
}
