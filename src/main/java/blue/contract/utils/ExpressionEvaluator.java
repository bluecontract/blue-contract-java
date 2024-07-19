package blue.contract.utils;

import blue.contract.model.WorkflowProcessingContext;
import blue.language.model.Node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ExpressionEvaluator {

    public enum ExpressionScope {
        GLOBAL,
        CURRENT_CONTRACT
    }

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("^\\$\\{(.+)\\}$");

    private final JSExecutor jsExecutor;

    public ExpressionEvaluator(JSExecutor jsExecutor) {
        this.jsExecutor = jsExecutor;
    }

    public Object evaluate(String expression, WorkflowProcessingContext context, ExpressionScope scope, boolean resolveFinalLink) {
        Matcher matcher = EXPRESSION_PATTERN.matcher(expression);
        if (matcher.matches()) {
            String jsExpression = matcher.group(1);
            return evaluateJSExpression(jsExpression, context, scope, resolveFinalLink);
        }
        return expression;
    }

    private Object evaluateJSExpression(String jsExpression, WorkflowProcessingContext context, ExpressionScope scope, boolean resolveFinalLink) {
        Map<String, Object> bindings = createBindings(context, scope, resolveFinalLink);

        try {
            return jsExecutor.executeScript(jsExpression, bindings);
        } catch (Exception e) {
            throw new RuntimeException("Error evaluating JS expression: " + jsExpression, e);
        }
    }

    public Object evaluateIfExpression(Object potentialExpression, WorkflowProcessingContext context, ExpressionScope scope, boolean resolveFinalLink) {
        if (potentialExpression instanceof String) {
            String strExpression = (String) potentialExpression;
            Matcher matcher = EXPRESSION_PATTERN.matcher(strExpression);
            if (matcher.matches()) {
                String jsExpression = matcher.group(1);
                return evaluateJSExpression(jsExpression, context, scope, resolveFinalLink);
            }
        }
        return potentialExpression;
    }

    public Node processNodeRecursively(Node node, WorkflowProcessingContext context, ExpressionScope scope, boolean resolveFinalLink) {
        if (node == null) {
            return null;
        }

        Node processedNode = node.clone();

        Object evaluatedValue = evaluateIfExpression(node.getValue(), context, scope, resolveFinalLink);
        processedNode.value(evaluatedValue);

        if (node.getItems() != null) {
            List<Node> processedItems = node.getItems().stream()
                    .map(item -> processNodeRecursively(item, context, scope, resolveFinalLink))
                    .collect(Collectors.toList());
            processedNode.items(processedItems);
        }

        if (node.getProperties() != null) {
            Map<String, Node> processedProperties = new HashMap<>();
            for (Map.Entry<String, Node> entry : node.getProperties().entrySet()) {
                String key = (String) evaluateIfExpression(entry.getKey(), context, scope, resolveFinalLink);
                Node value = processNodeRecursively(entry.getValue(), context, scope, resolveFinalLink);
                processedProperties.put(key, value);
            }
            processedNode.properties(processedProperties);
        }

        return processedNode;
    }

    private Map<String, Object> createBindings(WorkflowProcessingContext context, ExpressionScope scope, boolean resolveFinalLink) {
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("steps", context.getWorkflowInstance().getStepResults());

        if (scope == ExpressionScope.GLOBAL) {
            bindings.put("contract", (java.util.function.Function<String, Object>) path ->
                    context.getContractProcessingContext().accessContract(path, true, resolveFinalLink));
        } else {
            bindings.put("contract", (java.util.function.Function<String, Object>) path ->
                    context.getContractProcessingContext().accessContract(path, false, resolveFinalLink));
        }

        return bindings;
    }
}