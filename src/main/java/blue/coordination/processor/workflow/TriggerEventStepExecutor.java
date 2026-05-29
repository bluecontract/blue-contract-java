package blue.coordination.processor.workflow;

import blue.coordination.processor.bex.BexProcessingMetrics;
import blue.language.model.Node;
import blue.language.snapshot.FrozenNode;
import blue.repo.coordination.SequentialWorkflowStep;
import blue.repo.coordination.TriggerEvent;

import java.util.Map;

public final class TriggerEventStepExecutor implements WorkflowStepExecutor<TriggerEvent> {
    private final BexProcessingMetrics metrics;

    public TriggerEventStepExecutor() {
        this(null);
    }

    public TriggerEventStepExecutor(BexProcessingMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public boolean supports(SequentialWorkflowStep step) {
        return step instanceof TriggerEvent;
    }

    @Override
    public WorkflowStepResult execute(TriggerEvent step, StepExecutionContext context) {
        long stepStart = System.nanoTime();
        try {
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
            if (StaticPayloadValidator.rejectBexOperators(rawEvent,
                    context,
                    "Trigger Event event")) {
                return WorkflowStepResult.none();
            }
            Node event = step.getEvent();
            if (isEmpty(event)) {
                context.processorContext().throwFatal("Trigger Event step must declare event payload");
                return WorkflowStepResult.none();
            }
            long emitStart = System.nanoTime();
            context.processorContext().emitEvent(event.clone());
            if (metrics != null) {
                metrics.addTriggerEmitEventNanos(System.nanoTime() - emitStart);
            }
            return WorkflowStepResult.none();
        } finally {
            if (metrics != null) {
                metrics.addTriggerStepNanos(System.nanoTime() - stepStart);
            }
        }
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
}
