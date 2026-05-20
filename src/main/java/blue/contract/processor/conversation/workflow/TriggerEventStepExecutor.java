package blue.contract.processor.conversation.workflow;

import blue.bex.BexException;
import blue.bex.value.BexNodeWriter;
import blue.bex.value.BexValue;
import blue.contract.processor.conversation.bex.BexBindingReference;
import blue.contract.processor.conversation.bex.BexExpressionDetector;
import blue.contract.processor.conversation.bex.BexFieldEvaluator;
import blue.contract.processor.conversation.bex.BexProcessingMetrics;
import blue.contract.processor.conversation.expression.QuickJsExpressionResolver;
import blue.language.model.Node;
import blue.language.snapshot.FrozenNode;
import blue.language.utils.JsonPointer;
import blue.repo.conversation.SequentialWorkflowStep;
import blue.repo.conversation.TriggerEvent;
import blue.bex.result.BexExecutionResult;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class TriggerEventStepExecutor implements WorkflowStepExecutor<TriggerEvent> {
    private final QuickJsExpressionResolver resolver;
    private final BexExpressionDetector bexDetector;
    private final BexFieldEvaluator bexFieldEvaluator;
    private final long bexExpressionGasLimit;
    private final BexProcessingMetrics metrics;

    public TriggerEventStepExecutor() {
        this(new QuickJsExpressionResolver());
    }

    public TriggerEventStepExecutor(QuickJsExpressionResolver resolver) {
        this(resolver, null, null, 100_000L);
    }

    public TriggerEventStepExecutor(QuickJsExpressionResolver resolver,
                                    BexExpressionDetector bexDetector,
                                    BexFieldEvaluator bexFieldEvaluator,
                                    long bexExpressionGasLimit) {
        this(resolver, bexDetector, bexFieldEvaluator, bexExpressionGasLimit, null);
    }

    public TriggerEventStepExecutor(QuickJsExpressionResolver resolver,
                                    BexExpressionDetector bexDetector,
                                    BexFieldEvaluator bexFieldEvaluator,
                                    long bexExpressionGasLimit,
                                    BexProcessingMetrics metrics) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }
        if (bexExpressionGasLimit <= 0L) {
            throw new IllegalArgumentException("bexExpressionGasLimit must be positive");
        }
        this.resolver = resolver;
        this.bexDetector = bexDetector;
        this.bexFieldEvaluator = bexFieldEvaluator;
        this.bexExpressionGasLimit = bexExpressionGasLimit;
        this.metrics = metrics;
    }

    @Override
    public boolean supports(SequentialWorkflowStep step) {
        return step instanceof TriggerEvent;
    }

    @Override
    public WorkflowStepResult execute(TriggerEvent step, StepExecutionContext context) {
        if (step == null) {
            context.processorContext().throwFatal("Trigger Event step payload is invalid");
            return WorkflowStepResult.none();
        }
        if (metrics != null) {
            metrics.incrementTriggerEventStepsExecuted();
        }
        FrozenNode rawEvent = FrozenNodeUtil.property(context.stepFrozenNode(), "event");
        if (!hasDeclaredEvent(context.stepFrozenNode())) {
            context.processorContext().throwFatal("Trigger Event step must declare event payload");
            return WorkflowStepResult.none();
        }
        Node directEvent = directBindingEvent(rawEvent, context);
        if (directEvent != null) {
            if (metrics != null) {
                metrics.incrementDirectBexEventHits();
                metrics.incrementEventsEmitted();
            }
            context.processorContext().emitEvent(directEvent);
            return WorkflowStepResult.none();
        }
        if (bexDetector != null && bexFieldEvaluator != null && bexDetector.containsBex(rawEvent)) {
            emitBexEvent(rawEvent, context);
            return WorkflowStepResult.none();
        }
        Node event = step.getEvent();
        if (isEmpty(event)) {
            context.processorContext().throwFatal("Trigger Event step must declare event payload");
            return WorkflowStepResult.none();
        }
        Node resolvedEvent = resolver.resolve(event,
                context,
                includeAllPointers(),
                stopAtEmbeddedDocuments());
        if (resolvedEvent == null) {
            return WorkflowStepResult.none();
        }
        context.processorContext().emitEvent(resolvedEvent.clone());
        return WorkflowStepResult.none();
    }

    private void emitBexEvent(FrozenNode rawEvent, StepExecutionContext context) {
        try {
            if (metrics != null) {
                metrics.incrementGenericBexEventEvaluations();
            }
            BexValue value = bexFieldEvaluator.evaluateField(rawEvent, context, bexExpressionGasLimit);
            if (value.isUndefined() || value.isNull()) {
                context.processorContext().throwFatal("Trigger Event expression evaluated to undefined/null");
                return;
            }
            if (!value.isObject()) {
                context.processorContext().throwFatal("Trigger Event expression must evaluate to an object");
                return;
            }
            if (metrics != null) {
                metrics.incrementEventsEmitted();
            }
            context.processorContext().emitEvent(BexNodeWriter.toNode(value));
        } catch (BexException ex) {
            context.processorContext().throwFatal("Trigger Event expression failed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            context.processorContext().throwFatal("Trigger Event expression failed: " + ex.getMessage());
        }
    }

    private Node directBindingEvent(FrozenNode rawEvent, StepExecutionContext context) {
        BexBindingReference reference = BexBindingReference.parse(rawEvent);
        if (reference == null) {
            return null;
        }
        if ("event".equals(reference.name())) {
            Node value = nodeAt(context.eventRef(), reference.path());
            if (value == null || !isObjectLike(value)) {
                context.processorContext().throwFatal("Trigger Event expression must evaluate to an object");
                return null;
            }
            return value.clone();
        }
        if ("steps".equals(reference.name())) {
            StepPath stepPath = StepPath.parse(reference.path());
            if (stepPath == null) {
                return null;
            }
            Object result = context.stepResults().get(stepPath.stepName);
            if (!(result instanceof BexExecutionResult)) {
                return null;
            }
            BexValue value = ((BexExecutionResult) result).value().at(stepPath.valuePathSegments);
            if (value.isUndefined() || value.isNull()) {
                context.processorContext().throwFatal("Trigger Event expression evaluated to undefined/null");
                return null;
            }
            if (!value.isObject()) {
                context.processorContext().throwFatal("Trigger Event expression must evaluate to an object");
                return null;
            }
            return BexNodeWriter.toNode(value);
        }
        return null;
    }

    private static Predicate<String> includeAllPointers() {
        return new Predicate<String>() {
            @Override
            public boolean test(String pointer) {
                return true;
            }
        };
    }

    private static BiPredicate<String, Node> stopAtEmbeddedDocuments() {
        return new BiPredicate<String, Node>() {
            @Override
            public boolean test(String pointer, Node node) {
                return "/".equals(pointer) || !hasContracts(node);
            }
        };
    }

    private static boolean hasContracts(Node node) {
        return node != null
                && node.getProperties() != null
                && node.getProperties().containsKey("contracts");
    }

    private static Node nodeAt(Node root, String pointer) {
        if (root == null) {
            return null;
        }
        Node current = root;
        for (String segment : JsonPointer.split(pointer)) {
            if (current == null) {
                return null;
            }
            if (current.getProperties() != null && current.getProperties().containsKey(segment)) {
                current = current.getProperties().get(segment);
                continue;
            }
            if (current.getItems() != null && isArrayIndex(segment)) {
                int index = Integer.parseInt(segment);
                if (index < 0 || index >= current.getItems().size()) {
                    return null;
                }
                current = current.getItems().get(index);
                continue;
            }
            return null;
        }
        return current;
    }

    private static boolean isArrayIndex(String segment) {
        if (segment == null || segment.isEmpty()) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            if (!Character.isDigit(segment.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isObjectLike(Node node) {
        return node != null && (node.getProperties() != null || node.getType() != null);
    }

    private static Node property(Node node, String key) {
        return node != null && node.getProperties() != null ? node.getProperties().get(key) : null;
    }

    private static boolean hasDeclaredEvent(Node stepNode) {
        if (stepNode == null) {
            return true;
        }
        if (stepNode.getProperties() == null || !stepNode.getProperties().containsKey("event")) {
            return false;
        }
        return !isEmpty(stepNode.getProperties().get("event"));
    }

    private static boolean hasDeclaredEvent(FrozenNode stepNode) {
        if (stepNode == null) {
            return true;
        }
        if (stepNode.getProperties() == null || !stepNode.getProperties().containsKey("event")) {
            return false;
        }
        return !FrozenNodeUtil.isEmpty(stepNode.getProperties().get("event"));
    }

    private static boolean isEmpty(Node node) {
        if (node == null) {
            return true;
        }
        return node.getType() == null
                && node.getItemType() == null
                && node.getKeyType() == null
                && node.getValueType() == null
                && node.getValue() == null
                && empty(node.getItems())
                && empty(node.getProperties())
                && node.getBlueId() == null
                && node.getSchema() == null
                && node.getMergePolicy() == null
                && node.getPreviousBlueId() == null
                && node.getPosition() == null
                && node.getBlue() == null;
    }

    private static boolean empty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    private static boolean empty(Iterable<?> items) {
        return items == null || !items.iterator().hasNext();
    }

    private static final class StepPath {
        private final String stepName;
        private final List<String> valuePathSegments;

        private StepPath(String stepName, List<String> valuePathSegments) {
            this.stepName = stepName;
            this.valuePathSegments = valuePathSegments;
        }

        private static StepPath parse(String path) {
            List<String> segments = JsonPointer.split(path);
            if (segments.size() < 2) {
                return null;
            }
            return new StepPath(segments.get(0), segments.subList(1, segments.size()));
        }
    }
}
