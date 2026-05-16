package blue.contract.processor.conversation.workflow;

import blue.contract.processor.conversation.expression.QuickJsExpressionResolver;
import blue.language.model.Node;
import blue.repo.v1_3_0.conversation.SequentialWorkflowStep;
import blue.repo.v1_3_0.conversation.TriggerEvent;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class TriggerEventStepExecutor implements WorkflowStepExecutor<TriggerEvent> {
    private final QuickJsExpressionResolver resolver;

    public TriggerEventStepExecutor() {
        this(new QuickJsExpressionResolver());
    }

    public TriggerEventStepExecutor(QuickJsExpressionResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }
        this.resolver = resolver;
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
        if (!hasDeclaredEvent(context.stepNode())) {
            context.processorContext().throwFatal("Trigger Event step must declare event payload");
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

    private static boolean hasDeclaredEvent(Node stepNode) {
        if (stepNode == null) {
            return true;
        }
        if (stepNode.getProperties() == null || !stepNode.getProperties().containsKey("event")) {
            return false;
        }
        return !isEmpty(stepNode.getProperties().get("event"));
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
}
