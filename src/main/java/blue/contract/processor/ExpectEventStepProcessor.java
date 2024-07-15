package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.language.model.Node;

import java.util.Optional;

public class ExpectEventStepProcessor extends AbstractStepProcessor {

    public ExpectEventStepProcessor(Node step) {
        super(step);
    }

    @Override
    public Optional<WorkflowInstance> handleEvent(Node event, WorkflowProcessingContext context) {

        if (event.getType() == null || event.getType().getName() == null)
            return Optional.empty();
        
        String stepTypeName = step.getProperties().get("event").getType().getName();
        String eventTypeName = event.getType().getName();
        if (eventTypeName.equals(stepTypeName))
            return finalizeNextStepByOrder(event, context);
        else
            return Optional.empty();
    }

    @Override
    public Optional<WorkflowInstance> finalizeEvent(Node event, WorkflowProcessingContext workflowProcessingContext) {
        WorkflowInstance workflowInstance = workflowProcessingContext.getWorkflowInstance();
        workflowInstance.currentStepName(step.getName());
        
        return Optional.of(workflowInstance);
    }
}
