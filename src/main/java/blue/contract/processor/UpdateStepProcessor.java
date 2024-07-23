package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.model.WorkflowInstance;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.event.ContractProcessingEvent;
import blue.contract.model.event.ContractUpdateEvent;
import blue.contract.utils.Events;
import blue.contract.utils.ExpressionEvaluator;
import blue.contract.utils.ExpressionEvaluator.ExpressionScope;
import blue.language.model.Node;
import blue.language.utils.NodeToObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static blue.language.utils.NodeToObject.Strategy.SIMPLE;
import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class UpdateStepProcessor extends AbstractStepProcessor {

    private List<Map<String, Object>> rawChangeset;

    public UpdateStepProcessor(Node step, ExpressionEvaluator expressionEvaluator) {
        super(step, expressionEvaluator);
        prepareChangeset();
    }

    private void prepareChangeset() {
        this.rawChangeset = step.getProperties().get("changeset").getItems().stream()
                .map(e -> {
                    Map<String, Object> map = (Map<String, Object>) NodeToObject.get(e, SIMPLE);
                    if (map.containsKey("val")) {
                        map.put("value", map.remove("val"));
                    }
                    return map;
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
        Object obj = NodeToObject.get(context.getContractProcessingContext().getContract(), SIMPLE);
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

            ContractUpdateEvent updateEvent = new ContractUpdateEvent()
                    .changeset(JSON_MAPPER.convertValue(rawChangeset, Node.class));
            Node updateEventNode = YAML_MAPPER.convertValue(updateEvent, Node.class);
            ContractProcessingEvent processingEvent = Events.prepareContractProcessingEvent(updateEventNode, step.getName(), context);
            Node processingEventNode = YAML_MAPPER.convertValue(processingEvent, Node.class);
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