package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.model.Contract;
import blue.contract.model.GenericContract;
import blue.contract.model.WorkflowInstance;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.event.ContractProcessingEvent;
import blue.contract.model.event.ContractUpdateEvent;
import blue.contract.utils.Events;
import blue.contract.utils.ExpressionEvaluator;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.NodeToMapListOrValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static blue.language.utils.NodeToMapListOrValue.Strategy.SIMPLE;
import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class UpdateStepProcessor extends AbstractStepProcessor {

    private static final Set<String> ALLOWED_KEYS = Set.of("path", "value", "op");

    private List<Map<String, Object>> rawChangeset;

    public UpdateStepProcessor(Node step, ExpressionEvaluator expressionEvaluator) {
        super(step, expressionEvaluator);
        List<Map<String, Object>> changeset = step.getProperties().get("changeset").getItems().stream()
                .map(el -> (Map<String, Object>) NodeToMapListOrValue.get(el, SIMPLE))
                .toList();
        this.rawChangeset = prepareChangeset(changeset, false);
    }

    private List<Map<String, Object>> prepareChangeset(List<Map<String, Object>> changeset, boolean toVal) {
        return changeset.stream()
                .map(change -> {
                    Map<String, Object> newChange = new HashMap<>(change);
                    String fromKey = toVal ? "value" : "val";
                    String toKey = toVal ? "val" : "value";
                    if (newChange.containsKey(fromKey)) {
                        newChange.put(toKey, newChange.remove(fromKey));
                    }
                    return newChange;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Optional<WorkflowInstance> executeHandleStep(Node event, WorkflowProcessingContext context) {
        processEvent(event, context);
        return handleNextStepByOrder(event, context);
    }

    @Override
    public Optional<WorkflowInstance> executeFinalizeStep(Node event, WorkflowProcessingContext context) {
        processEvent(event, context);
        return finalizeNextStepByOrder(event, context);
    }

    private void processEvent(Node event, WorkflowProcessingContext context) {
        Blue blue = context.getContractProcessingContext().getBlue();
        // TODO: fix
        Node node = blue.objectToNode(context.getContractProcessingContext().getContract());
        Object obj = NodeToMapListOrValue.get(node, SIMPLE);
        JsonNode objNode = JSON_MAPPER.convertValue(obj, JsonNode.class);

        try {
            List<Map<String, Object>> evaluatedChangeset = rawChangeset.stream()
                    .map(change -> evaluateChange(change, context, blue))
                    .map(change -> change.entrySet().stream()
                            .filter(entry -> ALLOWED_KEYS.contains(entry.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                    .collect(Collectors.toList());
            // TODO: fix
            evaluatedChangeset.forEach(e -> e.remove("type"));

            List<Map<String, Object>> modifiedChangeset = new ArrayList<>();
            for (Map<String, Object> change : evaluatedChangeset) {
                String path = (String) change.get("path");
                if (path.endsWith("/-")) {
                    String parentPath = path.substring(0, path.length() - 2);
                    Node parentNode = node.getAsNode(parentPath);
                    if (parentNode == null || parentNode.getItems() == null) {
                        modifiedChangeset.add(createListOperation(parentPath));
                    }
                }
                modifiedChangeset.add(change);
            }

            JsonNode patchNode = JSON_MAPPER.convertValue(modifiedChangeset, JsonNode.class);
            JsonPatch patch = JsonPatch.fromJson(patchNode);

            JsonNode result = patch.apply(objNode);
            Node updatedContract = JSON_MAPPER.convertValue(result, Node.class);
            context.getContractProcessingContext().contract(blue.nodeToObject(updatedContract, GenericContract.class));

            List<Map<String, Object>> eventChangeset = prepareChangeset(evaluatedChangeset, true);
            ContractUpdateEvent updateEvent = new ContractUpdateEvent()
                    .changeset(JSON_MAPPER.convertValue(eventChangeset, Node.class));
            Node updateEventNode = context.getContractProcessingContext().getBlue().objectToNode(updateEvent);
            ContractProcessingEvent processingEvent = Events.prepareContractProcessingEvent(updateEventNode, step.getName(), context);
            Node processingEventNode = context.getContractProcessingContext().getBlue().objectToNode(processingEvent);
            context.getContractProcessingContext().getEmittedEvents().add(processingEventNode);
        } catch (IOException | JsonPatchException e) {
            throw new IllegalArgumentException("Applying JSON Patch failed", e);
        }
    }

    private Map<String, Object> evaluateChange(Map<String, Object> change, WorkflowProcessingContext context, Blue blue) {
        Map<String, Object> result = new HashMap<>();
        result.put("op", evaluateExpression(change.get("op"), context));
        result.put("path", evaluateExpression(change.get("path"), context));
        if (change.get("value") instanceof Map) {
            result.put("value", evaluateExpressionsRecursively(blue.objectToNode(change.get("value")), context));
        } else {
            result.put("value", evaluateExpression(change.get("value"), context));
        }
        return result;
    }

    private Map<String, Object> createListOperation(String path) {
        Map<String, Object> operation = new HashMap<>();
        operation.put("op", "add");
        operation.put("path", path);
        operation.put("value", new ArrayList<>());
        return operation;
    }
    
}