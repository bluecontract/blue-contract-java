package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.model.ContractProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.event.ContractProcessingEvent;
import blue.language.model.Node;

import java.util.Optional;

import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class TriggerEventStepProcessor extends AbstractStepProcessor {

    public TriggerEventStepProcessor(Node step) {
        super(step);
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
        ContractProcessingContext contractProcessingContext = workflowProcessingContext.getContractProcessingContext();
        ContractProcessingEvent processingEvent = new ContractProcessingEvent()
                .contractInstance(contractProcessingContext.getContractInstanceId())
                .workflowInstance(workflowProcessingContext.getWorkflowInstance().getId())
                .workflowStepName(step.getName())
                .initiateContractEntry(contractProcessingContext.getInitiateContractEntryBlueId())
                .initiateContractProcessingEntry(contractProcessingContext.getInitiateContractProcessingEntryBlueId())
                .event(eventNode);
        Node processingEventNode = YAML_MAPPER.convertValue(processingEvent, Node.class);
        workflowProcessingContext.getContractProcessingContext().getEmittedEvents().add(processingEventNode);
    }

}
