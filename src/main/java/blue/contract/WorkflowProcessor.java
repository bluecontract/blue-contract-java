package blue.contract;

import blue.contract.model.ContractProcessingContext;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.contract.utils.Workflows;
import blue.language.Blue;
import blue.language.model.Node;

import java.util.Optional;

import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

public class WorkflowProcessor {

    private StepProcessorProvider stepProcessorProvider;

    public WorkflowProcessor(StepProcessorProvider stepProcessorProvider) {
        this.stepProcessorProvider = stepProcessorProvider;
    }

    Optional<WorkflowInstance> processEvent(Node event, Node workflow, ContractProcessingContext contractProcessingContext) {
        WorkflowInstance workflowInstance = new WorkflowInstance(workflow);
        WorkflowProcessingContext context = new WorkflowProcessingContext(workflowInstance, contractProcessingContext, stepProcessorProvider);
        Node step = workflow.getProperties().get("trigger");
        if (step.getType() == null)
            step.type(new Node().name("Expect Event Step"));

        return handleEvent(event, step, context);

    }

    Optional<WorkflowInstance> processEvent(Node event, WorkflowInstance workflowInstance, ContractProcessingContext contractProcessingContext) {
        WorkflowProcessingContext context = new WorkflowProcessingContext(workflowInstance, contractProcessingContext, stepProcessorProvider);
        Optional<Node> step = Workflows.getStepByName(workflowInstance.getCurrentStepName(), workflowInstance.getWorkflow());

        if (!step.isPresent())
            throw new IllegalArgumentException("No step found with name '" + workflowInstance.getCurrentStepName() + "' in the workflow.");

        return handleEvent(event, step.get(), context);
    }

    private Optional<WorkflowInstance> handleEvent(Node event, Node step, WorkflowProcessingContext context) {
        Optional<StepProcessor> stepProcessor = stepProcessorProvider.getProcessor(step);
        if (!stepProcessor.isPresent())
            throw new IllegalArgumentException("No StepProcessor found for event: " +
                    JSON_MAPPER.disable(INDENT_OUTPUT).writeValueAsString(event));

        return stepProcessor.get().handleEvent(event, context);
    }

}