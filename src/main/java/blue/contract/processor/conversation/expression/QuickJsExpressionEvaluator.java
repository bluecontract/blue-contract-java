package blue.contract.processor.conversation.expression;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.JavaScriptExecutionException;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.javascript.JavaScriptValues;
import blue.contract.processor.conversation.javascript.NodeQuickJsRuntime;
import blue.contract.processor.conversation.javascript.QuickJsStepBindings;
import blue.contract.processor.conversation.javascript.QuickJsGas;
import blue.contract.processor.conversation.workflow.StepExecutionContext;
import blue.language.model.Node;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuickJsExpressionEvaluator implements ExpressionEvaluator {
    private static final Pattern FULL_EXPRESSION = Pattern.compile("^\\$\\{([\\s\\S]*)}$");

    private final JavaScriptRuntime runtime;
    private final long hostGasLimit;

    public QuickJsExpressionEvaluator() {
        this(new NodeQuickJsRuntime());
    }

    public QuickJsExpressionEvaluator(JavaScriptRuntime runtime) {
        this(runtime, QuickJsGas.DEFAULT_EXPRESSION_HOST_GAS_LIMIT);
    }

    public QuickJsExpressionEvaluator(JavaScriptRuntime runtime, long hostGasLimit) {
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
    public Node evaluate(Node value, StepExecutionContext context) {
        if (value == null) {
            return null;
        }
        Object raw = value.getValue();
        if (!(raw instanceof String)) {
            return value.clone();
        }
        Matcher expression = FULL_EXPRESSION.matcher((String) raw);
        if (!expression.matches()) {
            return value.clone();
        }
        JavaScriptEvaluationRequest request = new JavaScriptEvaluationRequest(
                expression.group(1).trim(),
                JavaScriptEvaluationRequest.Mode.EXPRESSION,
                QuickJsStepBindings.from(context),
                hostGasLimit);
        try {
            JavaScriptEvaluationResult result = runtime.evaluate(request);
            context.processorContext().consumeGas(result.hostGasUsed());
            return JavaScriptValues.toNode(result.value());
        } catch (JavaScriptExecutionException ex) {
            if (ex.hasGasUsage()) {
                context.processorContext().consumeGas(ex.hostGasUsed());
            }
            context.processorContext().throwFatal("QuickJS expression evaluation failed: " + ex.getMessage());
            return null;
        }
    }
}
