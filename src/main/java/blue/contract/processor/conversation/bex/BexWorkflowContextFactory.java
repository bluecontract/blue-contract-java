package blue.contract.processor.conversation.bex;

import blue.bex.api.BexExecutionContext;
import blue.bex.api.BexStepResults;
import blue.bex.result.BexExecutionResult;
import blue.bex.value.BexValue;
import blue.bex.value.BexValues;
import blue.contract.processor.conversation.workflow.StepExecutionContext;
import blue.language.model.Node;

import java.util.Map;

public final class BexWorkflowContextFactory {
    private final BexProcessingMetrics metrics;

    public BexWorkflowContextFactory() {
        this(null);
    }

    public BexWorkflowContextFactory(BexProcessingMetrics metrics) {
        this.metrics = metrics;
    }

    BexProcessingMetrics metrics() {
        return metrics;
    }

    public BexExecutionContext create(StepExecutionContext context, long gasLimit) {
        BexValue event = BexValues.nodeCursorTrustedImmutable(context.eventRef());
        BexValue currentContract = currentContractBinding(context);
        BexStepResults steps = stepResults(context.stepResults());
        return BexExecutionContext.builder()
                .document(new ScopedProcessorExecutionContextBexDocumentView(context, metrics))
                .event(event)
                .currentContract(currentContract)
                .steps(steps)
                .binding("event", event)
                .binding("steps", steps.asValue())
                .binding("currentContract", currentContract)
                .gasLimit(gasLimit)
                .build();
    }

    public BexStepResults stepResults(Map<String, Object> workflowStepResults) {
        BexStepResults.Builder builder = BexStepResults.builder();
        if (workflowStepResults == null) {
            return builder.build();
        }
        for (Map.Entry<String, Object> entry : workflowStepResults.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof BexExecutionResult) {
                builder.put(name, (BexExecutionResult) value);
            } else if (value instanceof Node) {
                builder.put(name, BexValues.nodeCursorTrustedImmutable((Node) value));
            } else {
                builder.put(name, BexValues.fromSimple(value));
            }
        }
        return builder.build();
    }

    public BexValue currentContractBinding(StepExecutionContext context) {
        BexValue base = context.currentContractFrozenNode() != null
                ? BexValues.frozen(context.currentContractFrozenNode())
                : BexValues.nodeCursorTrustedImmutable(context.currentContractNodeRef());
        String channel = context.workflow().getChannelKey();
        if (channel == null || channel.trim().isEmpty()) {
            return base;
        }
        BexValue existing = base.get("channel");
        if (!existing.isUndefined() && existing.isScalar() && !existing.asText().trim().isEmpty()) {
            return base;
        }
        return BexValues.overlay(base, "channel", BexValues.scalar(channel.trim()));
    }
}
