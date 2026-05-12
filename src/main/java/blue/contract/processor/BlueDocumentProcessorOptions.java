package blue.contract.processor;

import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.workflow.SequentialWorkflowRunner;

public final class BlueDocumentProcessorOptions {
    private final JavaScriptRuntime javaScriptRuntime;
    private final SequentialWorkflowRunner sequentialWorkflowRunner;

    private BlueDocumentProcessorOptions(Builder builder) {
        this.javaScriptRuntime = builder.javaScriptRuntime;
        this.sequentialWorkflowRunner = builder.sequentialWorkflowRunner;
    }

    public JavaScriptRuntime javaScriptRuntime() {
        return javaScriptRuntime;
    }

    public SequentialWorkflowRunner sequentialWorkflowRunner() {
        return sequentialWorkflowRunner;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private JavaScriptRuntime javaScriptRuntime;
        private SequentialWorkflowRunner sequentialWorkflowRunner;

        public Builder javaScriptRuntime(JavaScriptRuntime javaScriptRuntime) {
            this.javaScriptRuntime = javaScriptRuntime;
            return this;
        }

        public Builder sequentialWorkflowRunner(SequentialWorkflowRunner sequentialWorkflowRunner) {
            this.sequentialWorkflowRunner = sequentialWorkflowRunner;
            return this;
        }

        public BlueDocumentProcessorOptions build() {
            return new BlueDocumentProcessorOptions(this);
        }
    }
}
