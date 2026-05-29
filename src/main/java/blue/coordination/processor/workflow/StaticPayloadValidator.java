package blue.coordination.processor.workflow;

import blue.language.snapshot.FrozenNode;

import java.util.Map;

final class StaticPayloadValidator {
    private StaticPayloadValidator() {
    }

    static boolean rejectBexOperators(FrozenNode node, StepExecutionContext context, String fieldName) {
        String path = firstBexOperatorPath(node, "");
        if (path == null) {
            return false;
        }
        context.processorContext().throwFatal(fieldName + " must be static; BEX operator object is not allowed at " + path);
        return true;
    }

    private static String firstBexOperatorPath(FrozenNode node, String path) {
        if (node == null) {
            return null;
        }
        Map<String, FrozenNode> properties = node.getProperties();
        if (properties != null) {
            if (properties.size() == 1) {
                String key = properties.keySet().iterator().next();
                if (key != null && key.startsWith("$")) {
                    return path.isEmpty() ? "/" : path;
                }
            }
            for (Map.Entry<String, FrozenNode> entry : properties.entrySet()) {
                String found = firstBexOperatorPath(entry.getValue(), path + "/" + entry.getKey());
                if (found != null) {
                    return found;
                }
            }
        }
        if (node.getItems() != null) {
            for (int i = 0; i < node.getItems().size(); i++) {
                String found = firstBexOperatorPath(node.getItems().get(i), path + "/" + i);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
