package blue.coordination.processor;

import blue.bex.api.BexEngine;
import blue.coordination.processor.bex.BexProcessingMetrics;
import blue.coordination.processor.workflow.SequentialWorkflowRunner;

public final class CoordinationProcessorOptions {
    private final SequentialWorkflowRunner sequentialWorkflowRunner;
    private final BexEngine bexEngine;
    private final long defaultComputeGasLimit;
    private final BexProcessingMetrics processingMetrics;

    private CoordinationProcessorOptions(Builder builder) {
        this.sequentialWorkflowRunner = builder.sequentialWorkflowRunner;
        this.bexEngine = builder.bexEngine;
        this.defaultComputeGasLimit = builder.defaultComputeGasLimit;
        this.processingMetrics = builder.processingMetrics;
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

    public BexProcessingMetrics processingMetrics() {
        return processingMetrics;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private SequentialWorkflowRunner sequentialWorkflowRunner;
        private BexEngine bexEngine;
        private long defaultComputeGasLimit = 100_000L;
        private BexProcessingMetrics processingMetrics;

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

        public Builder processingMetrics(BexProcessingMetrics processingMetrics) {
            this.processingMetrics = processingMetrics;
            return this;
        }

        public CoordinationProcessorOptions build() {
            return new CoordinationProcessorOptions(this);
        }
    }
}
