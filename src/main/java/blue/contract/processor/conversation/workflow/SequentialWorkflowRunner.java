package blue.contract.processor.conversation.workflow;

import blue.bex.api.BexEngine;
import blue.contract.processor.conversation.bex.BexExpressionDetector;
import blue.contract.processor.conversation.bex.BexFieldEvaluator;
import blue.contract.processor.conversation.bex.BexProcessingMetrics;
import blue.contract.processor.conversation.bex.BexWorkflowContextFactory;
import blue.contract.processor.conversation.expression.QuickJsExpressionResolver;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.javascript.NodeQuickJsRuntime;
import blue.language.processor.ProcessorExecutionContext;
import blue.language.processor.WorkingDocument;
import blue.language.snapshot.FrozenNode;
import blue.repo.conversation.Compute;
import blue.repo.conversation.JavaScriptCode;
import blue.repo.conversation.SequentialWorkflow;
import blue.repo.conversation.SequentialWorkflowStep;
import blue.repo.conversation.TriggerEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SequentialWorkflowRunner {
    private final List<WorkflowStepExecutor<? extends SequentialWorkflowStep>> executors;
    private final BexProcessingMetrics metrics;

    public SequentialWorkflowRunner() {
        this(defaultExecutors());
    }

    public SequentialWorkflowRunner(List<WorkflowStepExecutor<? extends SequentialWorkflowStep>> executors) {
        this(executors, null);
    }

    private SequentialWorkflowRunner(List<WorkflowStepExecutor<? extends SequentialWorkflowStep>> executors,
                                     BexProcessingMetrics metrics) {
        if (executors == null) {
            throw new IllegalArgumentException("executors must not be null");
        }
        this.executors = Collections.unmodifiableList(new ArrayList<WorkflowStepExecutor<? extends SequentialWorkflowStep>>(executors));
        this.metrics = metrics;
    }

    public void execute(SequentialWorkflow workflow, ProcessorExecutionContext context) {
        long start = System.nanoTime();
        try {
            if (workflow.getSteps() == null) {
                return;
            }
            Map<String, Object> stepResults = new LinkedHashMap<String, Object>();
            FrozenNode contractNode = rawContractNode(context);
            List<FrozenNode> stepNodes = stepNodes(contractNode);
            List<SequentialWorkflowStep> steps = workflow.getSteps();
            WorkingDocument workingDocument = rootWorkingDocument(context);
            for (int i = 0; i < steps.size(); i++) {
                SequentialWorkflowStep step = steps.get(i);
                FrozenNode stepNode = i < stepNodes.size() ? stepNodes.get(i) : null;
                if (metrics != null) {
                    metrics.incrementWorkflowStepsExecuted();
                }
                WorkflowStepResult result = executeStep(workflow, step, stepNode, contractNode, i, stepResults, context, workingDocument);
                if (result != null && result.hasValue()) {
                    stepResults.put(stepKey(stepNode, i), result.value());
                }
            }
        } finally {
            if (metrics != null) {
                metrics.addWorkflowRunnerNanos(System.nanoTime() - start);
            }
        }
    }

    private WorkflowStepResult executeStep(SequentialWorkflow workflow,
                                           SequentialWorkflowStep step,
                                           FrozenNode stepNode,
                                           FrozenNode contractNode,
                                           int stepIndex,
                                           Map<String, Object> stepResults,
                                           ProcessorExecutionContext context,
                                           WorkingDocument workingDocument) {
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
                        stepResults,
                        workingDocument);
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
        if (step instanceof Compute) {
            return "Conversation/Compute";
        }
        if (step instanceof JavaScriptCode) {
            return "Conversation/JavaScript Code";
        }
        return step.getClass().getName();
    }

    private static List<WorkflowStepExecutor<? extends SequentialWorkflowStep>> defaultExecutors() {
        JavaScriptRuntime runtime = new NodeQuickJsRuntime();
        return executorsFor(runtime, BexEngine.builder().build(), 100_000L, 100_000L);
    }

    public static SequentialWorkflowRunner withJavaScriptRuntime(JavaScriptRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        return new SequentialWorkflowRunner(executorsFor(runtime, BexEngine.builder().build(), 100_000L, 100_000L));
    }

    public static SequentialWorkflowRunner withBexEngine(BexEngine bexEngine) {
        if (bexEngine == null) {
            throw new IllegalArgumentException("bexEngine must not be null");
        }
        return new SequentialWorkflowRunner(executorsFor(new NodeQuickJsRuntime(), bexEngine, 100_000L, 100_000L));
    }

    public static SequentialWorkflowRunner withRuntimes(JavaScriptRuntime runtime,
                                                        BexEngine bexEngine,
                                                        long computeGasLimit) {
        return withRuntimes(runtime, bexEngine, computeGasLimit, computeGasLimit);
    }

    public static SequentialWorkflowRunner withRuntimes(JavaScriptRuntime runtime,
                                                        BexEngine bexEngine,
                                                        long computeGasLimit,
                                                        long bexExpressionGasLimit) {
        return withRuntimes(runtime, bexEngine, computeGasLimit, bexExpressionGasLimit, null);
    }

    public static SequentialWorkflowRunner withRuntimes(JavaScriptRuntime runtime,
                                                        BexEngine bexEngine,
                                                        long computeGasLimit,
                                                        long bexExpressionGasLimit,
                                                        BexProcessingMetrics metrics) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        if (bexEngine == null) {
            throw new IllegalArgumentException("bexEngine must not be null");
        }
        if (computeGasLimit <= 0L) {
            throw new IllegalArgumentException("computeGasLimit must be positive");
        }
        if (bexExpressionGasLimit <= 0L) {
            throw new IllegalArgumentException("bexExpressionGasLimit must be positive");
        }
        return new SequentialWorkflowRunner(executorsFor(runtime, bexEngine, computeGasLimit, bexExpressionGasLimit, metrics), metrics);
    }

    private static List<WorkflowStepExecutor<? extends SequentialWorkflowStep>> executorsFor(JavaScriptRuntime runtime,
                                                                                            BexEngine bexEngine,
                                                                                            long computeGasLimit,
                                                                                            long bexExpressionGasLimit) {
        return executorsFor(runtime, bexEngine, computeGasLimit, bexExpressionGasLimit, null);
    }

    private static List<WorkflowStepExecutor<? extends SequentialWorkflowStep>> executorsFor(JavaScriptRuntime runtime,
                                                                                            BexEngine bexEngine,
                                                                                            long computeGasLimit,
                                                                                            long bexExpressionGasLimit,
                                                                                            BexProcessingMetrics metrics) {
        QuickJsExpressionResolver resolver = new QuickJsExpressionResolver(runtime);
        BexWorkflowContextFactory bexContextFactory = new BexWorkflowContextFactory(metrics);
        BexExpressionDetector bexDetector = new BexExpressionDetector();
        BexFieldEvaluator bexFieldEvaluator = new BexFieldEvaluator(bexEngine, bexContextFactory, bexExpressionGasLimit);
        return Arrays.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>asList(
                new TriggerEventStepExecutor(resolver, bexDetector, bexFieldEvaluator, bexExpressionGasLimit, metrics),
                new ComputeStepExecutor(bexEngine,
                        computeGasLimit,
                        new ComputeDefinitionResolver(metrics),
                        bexContextFactory,
                        new ComputeResultEmitter(),
                        metrics),
                new JavaScriptCodeStepExecutor(runtime),
                new UpdateDocumentStepExecutor(resolver, bexDetector, bexFieldEvaluator, bexExpressionGasLimit, metrics));
    }

    private List<FrozenNode> stepNodes(FrozenNode contractNode) {
        if (contractNode == null || contractNode.getProperties() == null) {
            return Collections.emptyList();
        }
        FrozenNode steps = contractNode.getProperties().get("steps");
        if (steps == null || steps.getItems() == null) {
            return Collections.emptyList();
        }
        return steps.getItems();
    }

    private String stepKey(FrozenNode stepNode, int index) {
        if (stepNode != null && stepNode.getName() != null && !stepNode.getName().trim().isEmpty()) {
            return stepNode.getName().trim();
        }
        return "Step" + (index + 1);
    }

    private FrozenNode rawContractNode(ProcessorExecutionContext context) {
        String pointer = contractPointer(context);
        if (pointer == null) {
            return context.frozenContractNode();
        }
        FrozenNode frozen = context.canonicalFrozenAt(pointer);
        return frozen != null ? frozen : context.frozenContractNode();
    }

    private WorkingDocument rootWorkingDocument(ProcessorExecutionContext context) {
        WorkingDocument workingDocument = context.newWorkingDocument();
        if (metrics != null) {
            if (workingDocument.usedMaterializedFallback()) {
                metrics.incrementWorkflowDocumentViewsFromDocument();
            } else {
                metrics.incrementWorkflowDocumentViewsFromFrozen();
            }
        }
        return workingDocument;
    }

    private String contractPointer(ProcessorExecutionContext context) {
        String key = context.contractKey();
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        String scope = context.scopePath();
        String contracts = appendPointer(scope == null || scope.trim().isEmpty() ? "/" : scope, "contracts");
        return appendPointer(contracts, key.trim());
    }

    private String appendPointer(String parent, String segment) {
        String escaped = segment.replace("~", "~0").replace("/", "~1");
        if (parent == null || parent.isEmpty() || "/".equals(parent)) {
            return "/" + escaped;
        }
        return parent + "/" + escaped;
    }
}
