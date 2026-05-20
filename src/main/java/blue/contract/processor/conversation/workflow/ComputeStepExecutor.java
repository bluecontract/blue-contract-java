package blue.contract.processor.conversation.workflow;

import blue.bex.BexException;
import blue.bex.api.BexEngine;
import blue.bex.api.BexExecutionContext;
import blue.bex.api.BexProgramSource;
import blue.bex.result.BexExecutionResult;
import blue.contract.processor.conversation.bex.BexProcessingMetrics;
import blue.contract.processor.conversation.bex.BexWorkflowContextFactory;
import blue.language.model.Node;
import blue.language.snapshot.FrozenNode;
import blue.repo.conversation.Compute;
import blue.repo.conversation.SequentialWorkflowStep;

public final class ComputeStepExecutor implements WorkflowStepExecutor<Compute> {
    private final BexEngine bexEngine;
    private final long defaultGasLimit;
    private final ComputeDefinitionResolver definitionResolver;
    private final BexWorkflowContextFactory contextFactory;
    private final ComputeResultEmitter resultEmitter;
    private final BexProcessingMetrics metrics;
    private final ComputeProgramNormalizer programNormalizer = new ComputeProgramNormalizer();

    public ComputeStepExecutor() {
        this(BexEngine.builder().build(), 100_000L);
    }

    public ComputeStepExecutor(BexEngine bexEngine, long defaultGasLimit) {
        this(bexEngine,
                defaultGasLimit,
                new ComputeDefinitionResolver(),
                new BexWorkflowContextFactory(),
                new ComputeResultEmitter(),
                null);
    }

    ComputeStepExecutor(BexEngine bexEngine,
                        long defaultGasLimit,
                        ComputeDefinitionResolver definitionResolver,
                        BexWorkflowContextFactory contextFactory,
                        ComputeResultEmitter resultEmitter,
                        BexProcessingMetrics metrics) {
        if (bexEngine == null) {
            throw new IllegalArgumentException("bexEngine must not be null");
        }
        if (defaultGasLimit <= 0L) {
            throw new IllegalArgumentException("defaultGasLimit must be positive");
        }
        if (definitionResolver == null) {
            throw new IllegalArgumentException("definitionResolver must not be null");
        }
        if (contextFactory == null) {
            throw new IllegalArgumentException("contextFactory must not be null");
        }
        if (resultEmitter == null) {
            throw new IllegalArgumentException("resultEmitter must not be null");
        }
        this.bexEngine = bexEngine;
        this.defaultGasLimit = defaultGasLimit;
        this.definitionResolver = definitionResolver;
        this.contextFactory = contextFactory;
        this.resultEmitter = resultEmitter;
        this.metrics = metrics;
    }

    @Override
    public boolean supports(SequentialWorkflowStep step) {
        return step instanceof Compute;
    }

    @Override
    public WorkflowStepResult execute(Compute step, StepExecutionContext context) {
        try {
            if (metrics != null) {
                metrics.incrementComputeStepsExecuted();
            }
            FrozenNode programNode = context.stepFrozenNode();
            if (programNode == null) {
                Node fallback = context.stepNodeRef();
                programNode = fallback != null ? FrozenNode.fromResolvedNode(fallback) : null;
            }
            if (programNode == null) {
                context.processorContext().throwFatal("Compute step must have a raw step node");
                return WorkflowStepResult.none();
            }
            FrozenNode definitionNode = definitionResolver.resolve(programNode, context);
            if (requiresMaterializedProgram(programNode, definitionNode)) {
                Node stepNode = context.stepNodeRef();
                programNode = stepNode != null
                        ? FrozenNode.fromResolvedNode(programNormalizer.program(stepNode))
                        : programNode;
                if (metrics != null) {
                    metrics.incrementComputeProgramNormalizations();
                }
            }
            String entry = FrozenNodeUtil.textProperty(programNode, "entry");
            BexProgramSource source = definitionNode != null
                    ? BexProgramSource.withDefinition(programNode, definitionNode, entry)
                    : BexProgramSource.inline(programNode);
            BexExecutionContext bexContext = contextFactory.create(context, computeGasLimit(programNode));
            BexExecutionResult result = bexEngine.compileAndExecute(source, bexContext);
            if (result.gasUsed() > 0L) {
                context.processorContext().consumeGas(result.gasUsed());
            }
            if (FrozenNodeUtil.booleanProperty(programNode, "emitEvents", true)) {
                int emitted = resultEmitter.emit(result, context);
                if (metrics != null) {
                    for (int i = 0; i < emitted; i++) {
                        metrics.incrementEventsEmitted();
                    }
                }
            }
            if (!FrozenNodeUtil.booleanProperty(programNode, "returnResult", true)) {
                return WorkflowStepResult.none();
            }
            return WorkflowStepResult.value(result);
        } catch (BexException ex) {
            context.processorContext().throwFatal("Compute failed: " + ex.getMessage());
            return WorkflowStepResult.none();
        } catch (RuntimeException ex) {
            context.processorContext().throwFatal("Compute failed: " + ex.getMessage());
            return WorkflowStepResult.none();
        }
    }

    private long computeGasLimit(FrozenNode stepNode) {
        Long parsed = FrozenNodeUtil.integer(FrozenNodeUtil.property(stepNode, "gasLimit"));
        if (parsed == null) {
            return defaultGasLimit;
        }
        if (parsed.longValue() <= 0L) {
            throw new BexException("Compute gasLimit must be positive");
        }
        return parsed.longValue();
    }

    private boolean requiresMaterializedProgram(FrozenNode programNode, FrozenNode definitionNode) {
        if (programNode == null) {
            return true;
        }
        if (definitionNode == null) {
            return true;
        }
        return FrozenNodeUtil.property(programNode, "do") != null
                || FrozenNodeUtil.property(programNode, "expr") != null
                || FrozenNodeUtil.property(programNode, "functions") != null
                || FrozenNodeUtil.property(programNode, "constants") != null;
    }
}
