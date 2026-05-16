package blue.contract.processor.conversation.workflow;

import blue.contract.processor.conversation.expression.ExpressionEvaluator;
import blue.language.model.Node;
import blue.language.processor.model.JsonPatch;
import blue.repo.v1_3_0.conversation.SequentialWorkflowStep;
import blue.repo.v1_3_0.conversation.UpdateDocument;
import blue.repo.v1_3_0.core.JsonPatchEntry;

public final class UpdateDocumentStepExecutor implements WorkflowStepExecutor<UpdateDocument> {
    private final ExpressionEvaluator expressionEvaluator;

    public UpdateDocumentStepExecutor(ExpressionEvaluator expressionEvaluator) {
        if (expressionEvaluator == null) {
            throw new IllegalArgumentException("expressionEvaluator must not be null");
        }
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public boolean supports(SequentialWorkflowStep step) {
        return step instanceof UpdateDocument;
    }

    @Override
    public WorkflowStepResult execute(UpdateDocument step, StepExecutionContext context) {
        if (step.getChangeset() == null) {
            return WorkflowStepResult.none();
        }
        for (JsonPatchEntry entry : step.getChangeset()) {
            context.processorContext().applyPatch(toPatch(entry, context));
        }
        return WorkflowStepResult.none();
    }

    private JsonPatch toPatch(JsonPatchEntry entry, StepExecutionContext context) {
        if (entry == null) {
            context.processorContext().throwFatal("Update Document changeset contains a null patch entry");
            return null;
        }
        String op = entry.getOp();
        String path = entry.getPath();
        if (op == null || op.trim().isEmpty()) {
            context.processorContext().throwFatal("Update Document patch operation is required");
            return null;
        }
        if (path == null || path.trim().isEmpty()) {
            context.processorContext().throwFatal("Update Document patch path is required");
            return null;
        }
        String absolutePath = context.processorContext().resolvePointer(path);
        String normalizedOp = op.trim().toLowerCase();
        if ("remove".equals(normalizedOp)) {
            return JsonPatch.remove(absolutePath);
        }
        Node value = expressionEvaluator.evaluate(entry.getVal(), context);
        if (value == null) {
            context.processorContext().throwFatal("Update Document patch value is required for operation: " + op);
            return null;
        }
        if ("add".equals(normalizedOp)) {
            return JsonPatch.add(absolutePath, value);
        }
        if ("replace".equals(normalizedOp)) {
            return JsonPatch.replace(absolutePath, value);
        }
        context.processorContext().throwFatal("Unsupported Update Document patch operation: " + op);
        return null;
    }
}
