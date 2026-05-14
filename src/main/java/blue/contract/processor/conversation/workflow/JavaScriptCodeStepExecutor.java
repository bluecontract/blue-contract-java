package blue.contract.processor.conversation.workflow;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.JavaScriptExecutionException;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.javascript.JavaScriptValues;
import blue.contract.processor.conversation.javascript.NodeQuickJsRuntime;
import blue.contract.processor.conversation.javascript.QuickJsGas;
import blue.contract.processor.conversation.javascript.QuickJsStepBindings;
import blue.repo.v1_2_0.conversation.JavaScriptCode;
import blue.repo.v1_2_0.conversation.SequentialWorkflowStep;
import java.util.List;
import java.util.Map;

public final class JavaScriptCodeStepExecutor implements WorkflowStepExecutor<JavaScriptCode> {
    private final JavaScriptRuntime runtime;
    private final long hostGasLimit;

    public JavaScriptCodeStepExecutor() {
        this(new NodeQuickJsRuntime());
    }

    public JavaScriptCodeStepExecutor(JavaScriptRuntime runtime) {
        this(runtime, QuickJsGas.DEFAULT_CODE_HOST_GAS_LIMIT);
    }

    public JavaScriptCodeStepExecutor(JavaScriptRuntime runtime, long hostGasLimit) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        if (hostGasLimit <= 0L) {
            throw new IllegalArgumentException("hostGasLimit must be positive");
        }
        this.runtime = runtime;
        this.hostGasLimit = hostGasLimit;
    }

    @Override
    public boolean supports(SequentialWorkflowStep step) {
        return step instanceof JavaScriptCode;
    }

    @Override
    public WorkflowStepResult execute(JavaScriptCode step, StepExecutionContext context) {
        if (step == null) {
            context.processorContext().throwFatal("JavaScript Code step payload is invalid");
            return WorkflowStepResult.none();
        }
        String code = step.getCode();
        if (code == null || code.trim().isEmpty()) {
            context.processorContext().throwFatal("JavaScript Code step must include code to execute");
            return WorkflowStepResult.none();
        }
        JavaScriptEvaluationRequest request = new JavaScriptEvaluationRequest(
                code,
                JavaScriptEvaluationRequest.Mode.BLOCK,
                QuickJsStepBindings.from(context),
                hostGasLimit);
        try {
            JavaScriptEvaluationResult result = runtime.evaluate(request);
            context.processorContext().consumeGas(result.hostGasUsed());
            emitReturnedEvents(result.value(), context);
            return WorkflowStepResult.value(result.value());
        } catch (JavaScriptExecutionException ex) {
            if (ex.hasGasUsage()) {
                context.processorContext().consumeGas(ex.hostGasUsed());
            }
            context.processorContext().throwFatal("JavaScript Code execution failed: " + ex.getMessage());
            return WorkflowStepResult.none();
        }
    }

    @SuppressWarnings("unchecked")
    private void emitReturnedEvents(Object value, StepExecutionContext context) {
        if (!(value instanceof Map)) {
            return;
        }
        Map<String, Object> result = (Map<String, Object>) value;
        if (!result.containsKey("events")) {
            return;
        }
        Object events = result.get("events");
        if (events == null) {
            return;
        }
        if (!(events instanceof List)) {
            context.processorContext().throwFatal("JavaScript Code result events must be a list");
            return;
        }
        for (Object event : (List<Object>) events) {
            context.processorContext().emitEvent(JavaScriptValues.toNode(event));
        }
    }
}
