package blue.contract.processor.conversation.workflow;

public final class WorkflowStepResult {
    private static final WorkflowStepResult NONE = new WorkflowStepResult(false, null);

    private final boolean hasValue;
    private final Object value;

    private WorkflowStepResult(boolean hasValue, Object value) {
        this.hasValue = hasValue;
        this.value = value;
    }

    public static WorkflowStepResult none() {
        return NONE;
    }

    public static WorkflowStepResult value(Object value) {
        return new WorkflowStepResult(true, value);
    }

    public boolean hasValue() {
        return hasValue;
    }

    public Object value() {
        return value;
    }
}
