package blue.contract.processor.conversation.bex;

import blue.bex.api.BexEngine;
import blue.bex.api.BexExecutionContext;
import blue.bex.api.BexProgramSource;
import blue.bex.result.BexExecutionResult;
import blue.bex.value.BexValue;
import blue.contract.processor.conversation.workflow.StepExecutionContext;
import blue.language.model.Node;
import blue.language.snapshot.FrozenNode;

public final class BexFieldEvaluator {
    private final BexEngine bexEngine;
    private final BexWorkflowContextFactory contextFactory;
    private final long defaultGasLimit;

    public BexFieldEvaluator(BexEngine bexEngine,
                             BexWorkflowContextFactory contextFactory,
                             long defaultGasLimit) {
        if (bexEngine == null) {
            throw new IllegalArgumentException("bexEngine must not be null");
        }
        if (contextFactory == null) {
            throw new IllegalArgumentException("contextFactory must not be null");
        }
        if (defaultGasLimit <= 0L) {
            throw new IllegalArgumentException("defaultGasLimit must be positive");
        }
        this.bexEngine = bexEngine;
        this.contextFactory = contextFactory;
        this.defaultGasLimit = defaultGasLimit;
    }

    public BexValue evaluateField(Node fieldNode, StepExecutionContext context, long gasLimit) {
        BexProcessingMetrics metrics = contextFactory.metrics();
        if (metrics != null) {
            metrics.incrementBexSyntheticProgramMaterializations();
        }
        return executeProgram(BexProgramSource.inline(FrozenNode.fromResolvedNode(syntheticProgram(fieldNode))), context, gasLimit);
    }

    public BexValue evaluateField(FrozenNode fieldNode, StepExecutionContext context, long gasLimit) {
        return executeProgram(BexProgramSource.expression(fieldNode != null ? fieldNode : FrozenNode.empty()), context, gasLimit);
    }

    public BexValue evaluateField(Node fieldNode, StepExecutionContext context) {
        return evaluateField(fieldNode, context, defaultGasLimit);
    }

    private BexValue executeProgram(BexProgramSource source, StepExecutionContext context, long gasLimit) {
        if (gasLimit <= 0L) {
            throw new IllegalArgumentException("gasLimit must be positive");
        }
        BexExecutionContext bexContext = contextFactory.create(context, gasLimit);
        BexExecutionResult result = bexEngine.compileAndExecute(source, bexContext);
        if (result.gasUsed() > 0L) {
            context.processorContext().consumeGas(result.gasUsed());
        }
        return result.value();
    }

    private Node syntheticProgram(Node fieldNode) {
        return new Node().properties("expr", fieldNode != null ? fieldNode.clone() : new Node());
    }
}
