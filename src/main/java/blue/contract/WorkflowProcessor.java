package blue.contract;

import blue.contract.debug.DebugContext;
import blue.contract.debug.DebugContextAware;
import blue.contract.model.ContractProcessingContext;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.contract.debug.DebugInfo;
import blue.contract.model.step.ExpectEventStep;
import blue.contract.utils.Workflows;
import blue.language.model.Node;
import blue.language.utils.BlueIds;

import java.util.Optional;

import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

public class WorkflowProcessor {

    public enum ProcessingMode {
        HANDLE, FINALIZE
    }

    private StepProcessorProvider stepProcessorProvider;
    private DebugContext debugContext;

    public WorkflowProcessor(StepProcessorProvider stepProcessorProvider) {
        this(stepProcessorProvider, new DebugContext(false));
    }

    public WorkflowProcessor(StepProcessorProvider stepProcessorProvider, DebugContext debugContext) {
        this.stepProcessorProvider = stepProcessorProvider;
        this.debugContext = debugContext;
    }

    public Optional<WorkflowInstance> processEvent(Node event, Node workflow, ContractProcessingContext contractProcessingContext) {
        return processEvent(event, workflow, contractProcessingContext, ProcessingMode.HANDLE);
    }

    public Optional<WorkflowInstance> processEvent(Node event, Node workflow, ContractProcessingContext contractProcessingContext, ProcessingMode mode) {
        WorkflowInstance workflowInstance = new WorkflowInstance(workflow);
        WorkflowProcessingContext context = new WorkflowProcessingContext(workflowInstance, contractProcessingContext, stepProcessorProvider);
        Node step = workflow.getProperties().get("trigger");
        if (step.getType() == null)
            step.type(new Node().blueId(BlueIds.getBlueId(ExpectEventStep.class)
                    .orElseThrow(() -> new RuntimeException("No blueId found for 'Expect Event Step'."))));

        return handleEvent(event, step, context, mode);

    }

    public Optional<WorkflowInstance> processEvent(Node event, WorkflowInstance workflowInstance, ContractProcessingContext contractProcessingContext) {
        return processEvent(event, workflowInstance, contractProcessingContext, ProcessingMode.HANDLE);
    }

    public Optional<WorkflowInstance> processEvent(Node event, WorkflowInstance workflowInstance,
                                                   ContractProcessingContext contractProcessingContext, ProcessingMode mode) {
        WorkflowProcessingContext context = new WorkflowProcessingContext(workflowInstance, contractProcessingContext, stepProcessorProvider);
        Optional<Node> step = Workflows.getStepByName(workflowInstance.getCurrentStepName(), workflowInstance.getWorkflow());

        if (!step.isPresent())
            throw new IllegalArgumentException("No step found with name '" + workflowInstance.getCurrentStepName() + "' in the workflow.");

        return handleEvent(event, step.get(), context, mode);
    }

    private Optional<WorkflowInstance> handleEvent(Node event, Node step, WorkflowProcessingContext context, ProcessingMode mode) {

        Optional<StepProcessor> stepProcessor = stepProcessorProvider.getProcessor(step);
        if (!stepProcessor.isPresent())
            throw new IllegalArgumentException("No StepProcessor found for event: " +
                    JSON_MAPPER.disable(INDENT_OUTPUT).writeValueAsString(event));

        if (stepProcessor.get() instanceof DebugContextAware) {
            ((DebugContextAware) stepProcessor.get()).setDebugContext(debugContext);
        }

        context.beginTransaction();

        Optional<WorkflowInstance> result;
        if (mode == ProcessingMode.HANDLE) {
            result = stepProcessor.get().handleEvent(event, context);
        } else {
            result = stepProcessor.get().finalizeEvent(event, context);
        }

        if (result.isPresent()) {
            context.commitTransaction();
            return result;
        } else {
            context.rollbackTransaction();
            return Optional.empty();
        }
    }

}