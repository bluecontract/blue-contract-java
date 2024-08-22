package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.model.WorkflowInstance;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.event.ContractProcessingEvent;
import blue.contract.model.event.ContractUpdateEvent;
import blue.contract.utils.Events;
import blue.contract.utils.ExpressionEvaluator;
import blue.language.model.Node;
import blue.language.utils.NodeToMapListOrValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static blue.language.utils.NodeToMapListOrValue.Strategy.SIMPLE;
import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class UpdateStepProcessor extends AbstractStepProcessor {

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
    public Optional<WorkflowInstance> handleEvent(Node event, WorkflowProcessingContext context) {
        processEvent(event, context);
        return handleNextStepByOrder(event, context);
    }

    @Override
    public Optional<WorkflowInstance> finalizeEvent(Node event, WorkflowProcessingContext context) {
        processEvent(event, context);
        return finalizeNextStepByOrder(event, context);
    }

    private void processEvent(Node event, WorkflowProcessingContext context) {
        Object obj = NodeToMapListOrValue.get(context.getContractProcessingContext().getContract(), SIMPLE);
        JsonNode objNode = JSON_MAPPER.convertValue(obj, JsonNode.class);

        try {
            List<Map<String, Object>> evaluatedChangeset = rawChangeset.stream()
                    .map(change -> evaluateChange(change, context))
                    .collect(Collectors.toList());

            JsonNode patchNode = JSON_MAPPER.convertValue(evaluatedChangeset, JsonNode.class);
            JsonPatch patch = JsonPatch.fromJson(patchNode);

            JsonNode result = patch.apply(objNode);
            Node updatedContract = JSON_MAPPER.convertValue(result, Node.class);
            context.getContractProcessingContext().contract(updatedContract);

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

    private Map<String, Object> evaluateChange(Map<String, Object> change, WorkflowProcessingContext context) {
        return change.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> evaluateExpression(entry.getValue(), context)
                ));
    }
    
}