package blue.coordination.processor.workflow;

public final class WorkflowStepResult {
    private static final WorkflowStepResult NONE = new WorkflowStepResult(false, null, false);

    private final boolean hasValue;
    private final Object value;
    private final boolean changesetHandled;

    private WorkflowStepResult(boolean hasValue, Object value, boolean changesetHandled) {
        this.hasValue = hasValue;
        this.value = value;
        this.changesetHandled = changesetHandled;
    }

    public static WorkflowStepResult none() {
        return NONE;
    }

    public static WorkflowStepResult value(Object value) {
        return value(value, false);
    }

    public static WorkflowStepResult value(Object value, boolean changesetHandled) {
        return new WorkflowStepResult(true, value, changesetHandled);
    }

    public boolean hasValue() {
        return hasValue;
    }

    public Object value() {
        return value;
    }

    public boolean changesetHandled() {
        return changesetHandled;
    }
}
