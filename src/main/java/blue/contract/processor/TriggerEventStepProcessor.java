package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.model.ContractProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.event.ContractProcessingEvent;
import blue.contract.utils.ExpressionEvaluator;
import blue.language.model.Node;

import java.util.Optional;

import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class TriggerEventStepProcessor extends AbstractStepProcessor {

    public TriggerEventStepProcessor(Node step, ExpressionEvaluator expressionEvaluator) {
        super(step, expressionEvaluator);
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

    private void processEvent(Node event, WorkflowProcessingContext workflowProcessingContext) {
        Node eventNode = step.getProperties().get("event").clone();
        eventNode = evaluateExpressionsRecursively(eventNode, workflowProcessingContext);
        ContractProcessingContext contractProcessingContext = workflowProcessingContext.getContractProcessingContext();
        ContractProcessingEvent processingEvent = new ContractProcessingEvent()
                .contractInstanceId(contractProcessingContext.getContractInstanceId())
                .workflowInstanceId(workflowProcessingContext.getWorkflowInstance().getId())
                .workflowStepName(step.getName())
                .initiateContractEntry(contractProcessingContext.getInitiateContractEntryBlueId())
                .initiateContractProcessingEntry(contractProcessingContext.getInitiateContractProcessingEntryBlueId())
                .event(eventNode);
        Node processingEventNode = YAML_MAPPER.convertValue(processingEvent, Node.class);
        workflowProcessingContext.getContractProcessingContext().getEmittedEvents().add(processingEventNode);
    }

}
