package blue.contract.processor.conversation.expression;

import blue.contract.processor.conversation.workflow.StepExecutionContext;
import blue.language.model.Node;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SimpleExpressionEvaluator implements ExpressionEvaluator {
    private static final Pattern FULL_EXPRESSION = Pattern.compile("^\\$\\{(.+)}$");
    private static final Pattern BINARY_EXPRESSION = Pattern.compile("^(.+)\\s+([+-])\\s+(.+)$");
    private static final Pattern DOCUMENT_REFERENCE = Pattern.compile("^document\\('([^']*)'\\)$");

    @Override
    public Node evaluate(Node value, StepExecutionContext context) {
        if (value == null) {
            return null;
        }
        Object raw = value.getValue();
        if (!(raw instanceof String)) {
            return value.clone();
        }
        Matcher fullExpression = FULL_EXPRESSION.matcher((String) raw);
        if (!fullExpression.matches()) {
            return value.clone();
        }
        BigInteger result = evaluateIntegralExpression(fullExpression.group(1).trim(), context);
        return new Node().value(result);
    }

    private BigInteger evaluateIntegralExpression(String expression, StepExecutionContext context) {
        Matcher binary = BINARY_EXPRESSION.matcher(expression);
        if (!binary.matches()) {
            context.processorContext().throwFatal("Unsupported expression: " + expression);
            return null;
        }
        BigInteger left = readIntegralOperand(binary.group(1).trim(), context);
        BigInteger right = readIntegralOperand(binary.group(3).trim(), context);
        String operator = binary.group(2);
        if ("+".equals(operator)) {
            return left.add(right);
        }
        if ("-".equals(operator)) {
            return left.subtract(right);
        }
        context.processorContext().throwFatal("Unsupported expression operator: " + operator);
        return null;
    }

    private BigInteger readIntegralOperand(String operand, StepExecutionContext context) {
        Node node;
        if (operand.startsWith("event.")) {
            node = readEventPath(context.event(), operand.substring("event.".length()), context);
        } else {
            Matcher documentReference = DOCUMENT_REFERENCE.matcher(operand);
            if (!documentReference.matches()) {
                context.processorContext().throwFatal("Unsupported expression operand: " + operand);
                return null;
            }
            String pointer = documentReference.group(1);
            node = context.processorContext().documentAt(context.processorContext().resolvePointer(pointer));
        }
        return integralValue(node, operand, context);
    }

    private Node readEventPath(Node event, String path, StepExecutionContext context) {
        if (event == null || path == null || path.isEmpty()) {
            context.processorContext().throwFatal("Unsupported event expression path: event." + path);
            return null;
        }
        Node current = event;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (current == null || current.getProperties() == null || !current.getProperties().containsKey(part)) {
                context.processorContext().throwFatal("Event expression path not found: event." + path);
                return null;
            }
            current = current.getProperties().get(part);
        }
        return current;
    }

    private BigInteger integralValue(Node node, String operand, StepExecutionContext context) {
        if (node == null) {
            context.processorContext().throwFatal("Expression operand resolved to nothing: " + operand);
            return null;
        }
        Object value = node.getValue();
        if (value instanceof BigInteger) {
            return (BigInteger) value;
        }
        if (value instanceof BigDecimal) {
            BigDecimal decimal = (BigDecimal) value;
            try {
                return decimal.toBigIntegerExact();
            } catch (ArithmeticException ex) {
                context.processorContext().throwFatal("Expression operand is not an integer: " + operand);
                return null;
            }
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        if (node.getItems() != null || node.getProperties() != null) {
            context.processorContext().throwFatal("Expression operand is not a scalar integer: " + operand);
            return null;
        }
        context.processorContext().throwFatal("Expression operand is not an integer: " + operand);
        return null;
    }
}
