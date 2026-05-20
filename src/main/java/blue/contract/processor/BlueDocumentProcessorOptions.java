package blue.contract.processor;

import blue.bex.api.BexEngine;
import blue.contract.processor.conversation.bex.BexProcessingMetrics;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.workflow.SequentialWorkflowRunner;

public final class BlueDocumentProcessorOptions {
    private final JavaScriptRuntime javaScriptRuntime;
    private final SequentialWorkflowRunner sequentialWorkflowRunner;
    private final BexEngine bexEngine;
    private final long defaultComputeGasLimit;
    private final long defaultBexExpressionGasLimit;
    private final BexProcessingMetrics processingMetrics;

    private BlueDocumentProcessorOptions(Builder builder) {
        this.javaScriptRuntime = builder.javaScriptRuntime;
        this.sequentialWorkflowRunner = builder.sequentialWorkflowRunner;
        this.bexEngine = builder.bexEngine;
        this.defaultComputeGasLimit = builder.defaultComputeGasLimit;
        this.defaultBexExpressionGasLimit = builder.defaultBexExpressionGasLimit;
        this.processingMetrics = builder.processingMetrics;
    }

    public JavaScriptRuntime javaScriptRuntime() {
        return javaScriptRuntime;
    }

    public SequentialWorkflowRunner sequentialWorkflowRunner() {
        return sequentialWorkflowRunner;
    }

    public BexEngine bexEngine() {
        return bexEngine;
    }

    public long defaultComputeGasLimit() {
        return defaultComputeGasLimit;
    }

    public long defaultBexExpressionGasLimit() {
        return defaultBexExpressionGasLimit;
    }

    public BexProcessingMetrics processingMetrics() {
        return processingMetrics;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private JavaScriptRuntime javaScriptRuntime;
        private SequentialWorkflowRunner sequentialWorkflowRunner;
        private BexEngine bexEngine;
        private long defaultComputeGasLimit = 100_000L;
        private long defaultBexExpressionGasLimit = 100_000L;
        private BexProcessingMetrics processingMetrics;

        public Builder javaScriptRuntime(JavaScriptRuntime javaScriptRuntime) {
            this.javaScriptRuntime = javaScriptRuntime;
            return this;
        }

        public Builder sequentialWorkflowRunner(SequentialWorkflowRunner sequentialWorkflowRunner) {
            this.sequentialWorkflowRunner = sequentialWorkflowRunner;
            return this;
        }

        public Builder bexEngine(BexEngine bexEngine) {
            this.bexEngine = bexEngine;
            return this;
        }

        public Builder defaultComputeGasLimit(long defaultComputeGasLimit) {
            if (defaultComputeGasLimit <= 0L) {
                throw new IllegalArgumentException("defaultComputeGasLimit must be positive");
            }
            this.defaultComputeGasLimit = defaultComputeGasLimit;
            return this;
        }

        public Builder defaultBexExpressionGasLimit(long defaultBexExpressionGasLimit) {
            if (defaultBexExpressionGasLimit <= 0L) {
                throw new IllegalArgumentException("defaultBexExpressionGasLimit must be positive");
            }
            this.defaultBexExpressionGasLimit = defaultBexExpressionGasLimit;
            return this;
        }

        public Builder processingMetrics(BexProcessingMetrics processingMetrics) {
            this.processingMetrics = processingMetrics;
            return this;
        }

        public BlueDocumentProcessorOptions build() {
            return new BlueDocumentProcessorOptions(this);
        }
    }
}
