package blue.contract.utils;

import blue.contract.model.WorkflowProcessingContext;
import blue.language.model.Node;
import blue.language.utils.NodeToMapListOrValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static blue.language.utils.NodeToMapListOrValue.Strategy.SIMPLE;

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
            return jsExecutor.executeExpression(jsExpression, bindings);
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

        Node processedNode;

        Object evaluatedValue = evaluateIfExpression(node.getValue(), context, scope, resolveFinalLink);
        if (evaluatedValue instanceof Node) {
            processedNode = (Node) evaluatedValue;
        } else {
            processedNode = node.clone();
            processedNode.value(evaluatedValue);

        }

        processedNode.name(evaluateStringField("name", node.getName(), context, scope, resolveFinalLink));
        processedNode.description(evaluateStringField("description", node.getDescription(), context, scope, resolveFinalLink));
        processedNode.blueId(evaluateStringField("blueId", node.getBlueId(), context, scope, resolveFinalLink));

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

    private String evaluateStringField(String fieldName, Object fieldValue, WorkflowProcessingContext context, ExpressionScope scope, boolean resolveFinalLink) {
        Object evaluated = evaluateIfExpression(fieldValue, context, scope, resolveFinalLink);
        if (evaluated == null) {
            return null;
        }
        if (evaluated instanceof String) {
            return (String) evaluated;
        }
        if (evaluated instanceof Node && ((Node) evaluated).getValue() instanceof String) {
            return (String) ((Node) evaluated).getValue();
        }
        throw new RuntimeException("Illegal expression result type for field '" + fieldName + "': " +
                                   evaluated.getClass() + ", \"" + evaluated + "\"");
    }

    private Map<String, Object> createBindings(WorkflowProcessingContext context, ExpressionScope scope, boolean resolveFinalLink) {
        Map<String, Object> bindings = new HashMap<>();

        Map<String, Object> processedStepResults = new HashMap<>();
        if (context.getWorkflowInstance().getStepResults() != null) {
            for (Map.Entry<String, Object> entry : context.getWorkflowInstance().getStepResults().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Node) {
                    processedStepResults.put(entry.getKey(), NodeToMapListOrValue.get((Node) value));
                } else {
                    processedStepResults.put(entry.getKey(), value);
                }
            }
        }
        bindings.put("steps", processedStepResults);

        if (context.getContractProcessingContext().getIncomingEvent() != null) {
            bindings.put("event", NodeToMapListOrValue.get(context.getContractProcessingContext().getIncomingEvent(), SIMPLE));
        }

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