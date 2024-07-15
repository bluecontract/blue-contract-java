package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.language.model.Node;

import java.util.Optional;

import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

public class HelloEventProcessor extends AbstractStepProcessor {

    public HelloEventProcessor(Node step) {
        super(step);
    }

    @Override
    public Optional<WorkflowInstance> handleEvent(Node event, WorkflowProcessingContext context) {

        System.out.println("HelloEventProcessor, handleEvent...");
        System.out.println(JSON_MAPPER.disable(INDENT_OUTPUT).writeValueAsString(event));

        return finalizeNextStepByOrder(event, context);
    }

    @Override
    public Optional<WorkflowInstance> finalizeEvent(Node event, WorkflowProcessingContext workflowProcessingContext) {

        System.out.println("HelloEventProcessor, finalizeEvent...");
        System.out.println(JSON_MAPPER.disable(INDENT_OUTPUT).writeValueAsString(event));

        WorkflowInstance workflowInstance = workflowProcessingContext.getWorkflowInstance();
        workflowInstance.currentStepName(step.getName());
        
        return Optional.of(workflowInstance);
    }
}
