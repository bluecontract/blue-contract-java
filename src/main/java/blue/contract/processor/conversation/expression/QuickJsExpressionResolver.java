package blue.contract.processor.conversation.expression;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.JavaScriptExecutionException;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.javascript.JavaScriptValues;
import blue.contract.processor.conversation.javascript.NodeQuickJsRuntime;
import blue.contract.processor.conversation.javascript.QuickJsGas;
import blue.contract.processor.conversation.javascript.QuickJsStepBindings;
import blue.contract.processor.conversation.workflow.StepExecutionContext;
import blue.language.model.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuickJsExpressionResolver {
    private static final Pattern FULL_EXPRESSION = Pattern.compile("^\\$\\{([\\s\\S]*)}$");

    private final JavaScriptRuntime runtime;
    private final long hostGasLimit;

    public QuickJsExpressionResolver() {
        this(new NodeQuickJsRuntime());
    }

    public QuickJsExpressionResolver(JavaScriptRuntime runtime) {
        this(runtime, QuickJsGas.DEFAULT_EXPRESSION_HOST_GAS_LIMIT);
    }

    public QuickJsExpressionResolver(JavaScriptRuntime runtime, long hostGasLimit) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        if (hostGasLimit <= 0L) {
            throw new IllegalArgumentException("hostGasLimit must be positive");
        }
        this.runtime = runtime;
        this.hostGasLimit = hostGasLimit;
    }

    public Node resolve(Node value, StepExecutionContext context) {
        return resolve(value, context, all(), all());
    }

    public Node resolve(Node value,
                        StepExecutionContext context,
                        Predicate<String> include,
                        Predicate<String> shouldDescend) {
        return resolve(value, context, include, adapt(shouldDescend));
    }

    public Node resolve(Node value,
                        StepExecutionContext context,
                        Predicate<String> include,
                        BiPredicate<String, Node> shouldDescend) {
        EvaluationCounter counter = new EvaluationCounter();
        try {
            Node resolved = resolve(value,
                    QuickJsStepBindings.from(context),
                    include,
                    shouldDescend,
                    "/",
                    counter);
            if (counter.hostGasUsed > 0L) {
                context.processorContext().consumeGas(counter.hostGasUsed);
            }
            return resolved;
        } catch (JavaScriptExecutionException ex) {
            long hostGasUsed = counter.hostGasUsed;
            if (ex.hasGasUsage()) {
                hostGasUsed += ex.hostGasUsed();
            }
            if (hostGasUsed > 0L) {
                context.processorContext().consumeGas(hostGasUsed);
            }
            context.processorContext().throwFatal(ex.getMessage());
            return null;
        }
    }

    public Node resolve(Node value, Map<String, Object> bindings) {
        return resolve(value, bindings, all(), all());
    }

    public Node resolve(Node value,
                        Map<String, Object> bindings,
                        Predicate<String> include,
                        Predicate<String> shouldDescend) {
        return resolve(value, bindings, include, adapt(shouldDescend));
    }

    public Node resolve(Node value,
                        Map<String, Object> bindings,
                        Predicate<String> include,
                        BiPredicate<String, Node> shouldDescend) {
        return resolve(value, bindings, include, shouldDescend, "/", new EvaluationCounter());
    }

    private Node resolve(Node value,
                         Map<String, Object> bindings,
                         Predicate<String> include,
                         BiPredicate<String, Node> shouldDescend,
                         String pointer,
                         EvaluationCounter counter) {
        if (value == null) {
            return null;
        }
        Object raw = value.getValue();
        if (raw instanceof String) {
            return test(include, pointer) ? resolveString((String) raw, bindings, counter) : value.clone();
        }
        if (!test(shouldDescend, pointer, value)) {
            return value.clone();
        }
        if (value.getItems() != null) {
            List<Node> items = new ArrayList<Node>();
            for (int i = 0; i < value.getItems().size(); i++) {
                items.add(resolve(value.getItems().get(i),
                        bindings,
                        include,
                        shouldDescend,
                        append(pointer, Integer.toString(i)),
                        counter));
            }
            return copyMetadata(value).items(items);
        }
        if (value.getProperties() != null) {
            Map<String, Node> properties = new LinkedHashMap<String, Node>();
            for (Map.Entry<String, Node> entry : value.getProperties().entrySet()) {
                String childPointer = append(pointer, entry.getKey());
                if (test(shouldDescend, childPointer, entry.getValue())) {
                    properties.put(entry.getKey(), resolve(entry.getValue(),
                            bindings,
                            include,
                            shouldDescend,
                            childPointer,
                            counter));
                } else {
                    properties.put(entry.getKey(), entry.getValue().clone());
                }
            }
            return copyMetadata(value).properties(properties);
        }
        return value.clone();
    }

    private Node resolveString(String value, Map<String, Object> bindings, EvaluationCounter counter) {
        if (isExpression(value)) {
            Matcher full = FULL_EXPRESSION.matcher(value);
            full.matches();
            JavaScriptEvaluationResult result = evaluate(full.group(1).trim(), bindings);
            counter.hostGasUsed += result.hostGasUsed();
            return JavaScriptValues.toNode(result.value());
        }
        if (!value.contains("${")) {
            return new Node().value(value);
        }
        StringBuilder resolved = new StringBuilder();
        int position = 0;
        while (position < value.length()) {
            int start = value.indexOf("${", position);
            if (start < 0) {
                resolved.append(value.substring(position));
                break;
            }
            resolved.append(value.substring(position, start));
            int end = value.indexOf('}', start + 2);
            if (end < 0) {
                resolved.append(value.substring(start));
                break;
            }
            String expression = value.substring(start + 2, end).trim();
            JavaScriptEvaluationResult result = evaluate(expression, bindings);
            counter.hostGasUsed += result.hostGasUsed();
            resolved.append(result.value() != null ? String.valueOf(result.value()) : "null");
            position = end + 1;
        }
        return new Node().value(resolved.toString());
    }

    private JavaScriptEvaluationResult evaluate(String expression, Map<String, Object> bindings) {
        try {
            return runtime.evaluate(new JavaScriptEvaluationRequest(expression,
                    JavaScriptEvaluationRequest.Mode.EXPRESSION,
                    bindings,
                    hostGasLimit));
        } catch (JavaScriptExecutionException ex) {
            throw new JavaScriptExecutionException("QuickJS expression resolution failed: " + ex.getMessage(),
                    ex,
                    ex.hasGasUsage() ? ex.wasmGasUsed() : null,
                    ex.hasGasUsage() ? ex.hostGasUsed() : null);
        }
    }

    private Node copyMetadata(Node source) {
        return new Node()
                .name(source.getName())
                .description(source.getDescription())
                .type(source.getType() != null ? source.getType().clone() : null)
                .itemType(source.getItemType() != null ? source.getItemType().clone() : null)
                .keyType(source.getKeyType() != null ? source.getKeyType().clone() : null)
                .valueType(source.getValueType() != null ? source.getValueType().clone() : null)
                .blueId(source.getBlueId())
                .schema(source.getSchema())
                .mergePolicy(source.getMergePolicy())
                .previousBlueId(source.getPreviousBlueId())
                .position(source.getPosition())
                .blue(source.getBlue() != null ? source.getBlue().clone() : null)
                .contracts(source.getContracts() != null ? source.getContracts().clone() : null)
                .inlineValue(source.isInlineValue());
    }

    private boolean test(Predicate<String> predicate, String pointer) {
        return predicate == null || predicate.test(pointer);
    }

    private boolean test(BiPredicate<String, Node> predicate, String pointer, Node node) {
        return predicate == null || predicate.test(pointer, node);
    }

    private static Predicate<String> all() {
        return new Predicate<String>() {
            @Override
            public boolean test(String value) {
                return true;
            }
        };
    }

    private static BiPredicate<String, Node> adapt(final Predicate<String> predicate) {
        return new BiPredicate<String, Node>() {
            @Override
            public boolean test(String pointer, Node node) {
                return predicate == null || predicate.test(pointer);
            }
        };
    }

    private String append(String parent, String segment) {
        String escaped = segment.replace("~", "~0").replace("/", "~1");
        if (parent == null || "/".equals(parent)) {
            return "/" + escaped;
        }
        return parent + "/" + escaped;
    }

    private boolean isExpression(String value) {
        Matcher full = FULL_EXPRESSION.matcher(value);
        if (!full.matches()) {
            return false;
        }
        return value.indexOf("${") == value.lastIndexOf("${");
    }

    private static final class EvaluationCounter {
        private long hostGasUsed;
    }
}
