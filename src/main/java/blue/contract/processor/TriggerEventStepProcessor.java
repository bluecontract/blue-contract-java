package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.model.WorkflowInstance;
import blue.contract.model.WorkflowProcessingContext;
import blue.language.model.Node;

import java.util.Optional;

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
        workflowProcessingContext.getContractProcessingContext().getEmittedEvents().add(eventNode);
    }

}
