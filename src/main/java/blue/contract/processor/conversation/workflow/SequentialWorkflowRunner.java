package blue.contract.processor.conversation.workflow;

import blue.contract.processor.conversation.expression.QuickJsExpressionEvaluator;
import blue.contract.processor.conversation.expression.QuickJsExpressionResolver;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.javascript.NodeQuickJsRuntime;
import blue.language.processor.ProcessorExecutionContext;
import blue.language.model.Node;
import blue.repo.v1_3_0.conversation.JavaScriptCode;
import blue.repo.v1_3_0.conversation.SequentialWorkflow;
import blue.repo.v1_3_0.conversation.SequentialWorkflowStep;
import blue.repo.v1_3_0.conversation.TriggerEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SequentialWorkflowRunner {
    private final List<WorkflowStepExecutor<? extends SequentialWorkflowStep>> executors;

    public SequentialWorkflowRunner() {
        this(defaultExecutors());
    }

    public SequentialWorkflowRunner(List<WorkflowStepExecutor<? extends SequentialWorkflowStep>> executors) {
        if (executors == null) {
            throw new IllegalArgumentException("executors must not be null");
        }
        this.executors = Collections.unmodifiableList(new ArrayList<WorkflowStepExecutor<? extends SequentialWorkflowStep>>(executors));
    }

    public void execute(SequentialWorkflow workflow, ProcessorExecutionContext context) {
        if (workflow.getSteps() == null) {
            return;
        }
        Map<String, Object> stepResults = new LinkedHashMap<String, Object>();
        Node contractNode = context.contractNode();
        List<Node> stepNodes = stepNodes(contractNode);
        List<SequentialWorkflowStep> steps = workflow.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            SequentialWorkflowStep step = steps.get(i);
            Node stepNode = i < stepNodes.size() ? stepNodes.get(i) : null;
            WorkflowStepResult result = executeStep(workflow, step, stepNode, contractNode, i, stepResults, context);
            if (result != null && result.hasValue()) {
                stepResults.put(stepKey(stepNode, i), result.value());
            }
        }
    }

    private WorkflowStepResult executeStep(SequentialWorkflow workflow,
                                           SequentialWorkflowStep step,
                                           Node stepNode,
                                           Node contractNode,
                                           int stepIndex,
                                           Map<String, Object> stepResults,
                                           ProcessorExecutionContext context) {
        if (step == null) {
            context.throwFatal("Unsupported null sequential workflow step");
            return WorkflowStepResult.none();
        }
        for (WorkflowStepExecutor<? extends SequentialWorkflowStep> executor : executors) {
            if (executor.supports(step)) {
                StepExecutionContext stepContext = new StepExecutionContext(context,
                        workflow,
                        step,
                        stepNode,
                        contractNode,
                        stepIndex,
                        stepResults);
                return executeSupported(executor, step, stepContext);
            }
        }
        context.throwFatal("Unsupported sequential workflow step: " + stepName(step));
        return WorkflowStepResult.none();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private WorkflowStepResult executeSupported(WorkflowStepExecutor executor,
                                                SequentialWorkflowStep step,
                                                StepExecutionContext context) {
        return executor.execute(step, context);
    }

    private String stepName(SequentialWorkflowStep step) {
        if (step instanceof TriggerEvent) {
            return "Conversation/Trigger Event";
        }
        if (step instanceof JavaScriptCode) {
            return "Conversation/JavaScript Code";
        }
        return step.getClass().getName();
    }

    private static List<WorkflowStepExecutor<? extends SequentialWorkflowStep>> defaultExecutors() {
        JavaScriptRuntime runtime = new NodeQuickJsRuntime();
        return executorsFor(runtime);
    }

    public static SequentialWorkflowRunner withJavaScriptRuntime(JavaScriptRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        return new SequentialWorkflowRunner(executorsFor(runtime));
    }

    private static List<WorkflowStepExecutor<? extends SequentialWorkflowStep>> executorsFor(JavaScriptRuntime runtime) {
        QuickJsExpressionResolver resolver = new QuickJsExpressionResolver(runtime);
        return Arrays.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>asList(
                new TriggerEventStepExecutor(resolver),
                new JavaScriptCodeStepExecutor(runtime),
                new UpdateDocumentStepExecutor(new QuickJsExpressionEvaluator(runtime)));
    }

    private List<Node> stepNodes(Node contractNode) {
        if (contractNode == null || contractNode.getProperties() == null) {
            return Collections.emptyList();
        }
        Node steps = contractNode.getProperties().get("steps");
        if (steps == null || steps.getItems() == null) {
            return Collections.emptyList();
        }
        return steps.getItems();
    }

    private String stepKey(Node stepNode, int index) {
        if (stepNode != null && stepNode.getName() != null && !stepNode.getName().trim().isEmpty()) {
            return stepNode.getName().trim();
        }
        return "Step" + (index + 1);
    }
}
