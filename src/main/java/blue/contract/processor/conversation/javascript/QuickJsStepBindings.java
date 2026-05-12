package blue.contract.processor.conversation.javascript;

import blue.contract.processor.conversation.workflow.StepExecutionContext;
import blue.language.model.Node;
import java.util.LinkedHashMap;
import java.util.Map;

public final class QuickJsStepBindings {
    private QuickJsStepBindings() {
    }

    public static Map<String, Object> from(StepExecutionContext context) {
        Map<String, Object> bindings = new LinkedHashMap<String, Object>();
        Node event = context.event();
        Node document = context.processorContext().documentAt(context.processorContext().resolvePointer("/"));
        Node contractNode = context.currentContractNode();

        bindings.put("event", JavaScriptValues.simple(event));
        bindings.put("eventCanonical", JavaScriptValues.official(event));
        bindings.put("document", JavaScriptValues.official(document));
        bindings.put("documentCanonical", JavaScriptValues.official(document));
        bindings.put("documentMetadata", JavaScriptValues.metadataIndex(document));
        bindings.put("steps", JavaScriptValues.stepResults(context.stepResults()));
        bindings.put("currentContract", currentContract(context, contractNode));
        bindings.put("currentContractCanonical", currentContractCanonical(contractNode));
        return bindings;
    }

    @SuppressWarnings("unchecked")
    private static Object currentContract(StepExecutionContext context, Node contractNode) {
        Object simple = JavaScriptValues.simple(contractNode);
        if (!(simple instanceof Map)) {
            return simple;
        }
        Map<String, Object> copy = new LinkedHashMap<String, Object>((Map<String, Object>) simple);
        String channel = context.workflow().getChannelKey();
        if (channel != null && !channel.trim().isEmpty() && !copy.containsKey("channel")) {
            copy.put("channel", channel);
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object currentContractCanonical(Node contractNode) {
        Object canonical = JavaScriptValues.official(contractNode);
        if (!(canonical instanceof Map)) {
            return canonical;
        }
        Map<String, Object> copy = new LinkedHashMap<String, Object>((Map<String, Object>) canonical);
        wrapMetadataValue(copy, "name");
        wrapMetadataValue(copy, "description");
        return copy;
    }

    private static void wrapMetadataValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String)) {
            return;
        }
        Map<String, Object> wrapped = new LinkedHashMap<String, Object>();
        wrapped.put("value", value);
        map.put(key, wrapped);
    }
}
