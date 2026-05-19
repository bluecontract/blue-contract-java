package blue.contract.processor.conversation.workflow;

import blue.contract.processor.conversation.expression.ExpressionEvaluator;
import blue.contract.processor.conversation.expression.QuickJsExpressionResolver;
import blue.language.model.Node;
import blue.language.processor.model.JsonPatch;
import blue.repo.v1_3_0.conversation.SequentialWorkflowStep;
import blue.repo.v1_3_0.conversation.UpdateDocument;
import blue.repo.v1_3_0.core.JsonPatchEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class UpdateDocumentStepExecutor implements WorkflowStepExecutor<UpdateDocument> {
    private final QuickJsExpressionResolver resolver;
    private final ExpressionEvaluator expressionEvaluator;

    public UpdateDocumentStepExecutor(QuickJsExpressionResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }
        this.resolver = resolver;
        this.expressionEvaluator = null;
    }

    public UpdateDocumentStepExecutor(ExpressionEvaluator expressionEvaluator) {
        if (expressionEvaluator == null) {
            throw new IllegalArgumentException("expressionEvaluator must not be null");
        }
        this.resolver = null;
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public boolean supports(SequentialWorkflowStep step) {
        return step instanceof UpdateDocument;
    }

    @Override
    public WorkflowStepResult execute(UpdateDocument step, StepExecutionContext context) {
        List<JsonPatchEntry> changeset = changeset(step, context);
        if (changeset.isEmpty()) {
            return WorkflowStepResult.none();
        }
        for (JsonPatchEntry entry : changeset) {
            context.processorContext().applyPatch(toPatch(entry, context, resolver == null));
        }
        return WorkflowStepResult.none();
    }

    private List<JsonPatchEntry> changeset(UpdateDocument step, StepExecutionContext context) {
        if (resolver == null || context.stepNode() == null) {
            return legacyChangeset(step);
        }
        Node resolvedStep = resolver.resolve(context.stepNode(),
                context,
                changesetPointers(),
                path -> true);
        return extractChangeset(resolvedStep, context);
    }

    private List<JsonPatchEntry> legacyChangeset(UpdateDocument step) {
        if (step == null || step.getChangeset() == null) {
            return Collections.emptyList();
        }
        return step.getChangeset();
    }

    private List<JsonPatchEntry> extractChangeset(Node stepNode, StepExecutionContext context) {
        Node changeset = property(stepNode, "changeset");
        if (changeset == null) {
            return Collections.emptyList();
        }
        if (changeset.getItems() == null) {
            context.processorContext().throwFatal("Update Document changeset must be a list");
            return Collections.emptyList();
        }
        List<JsonPatchEntry> entries = new ArrayList<JsonPatchEntry>();
        for (Node item : changeset.getItems()) {
            entries.add(toEntry(item, context));
        }
        return entries;
    }

    private JsonPatchEntry toEntry(Node item, StepExecutionContext context) {
        if (item == null || item.getProperties() == null) {
            context.processorContext().throwFatal("Update Document changeset contains a null patch entry");
            return null;
        }
        Map<String, Node> properties = item.getProperties();
        return new JsonPatchEntry()
                .op(text(properties.get("op")))
                .path(text(properties.get("path")))
                .val(properties.containsKey("val") && properties.get("val") != null
                        ? properties.get("val").clone()
                        : null);
    }

    private JsonPatch toPatch(JsonPatchEntry entry, StepExecutionContext context, boolean evaluateValue) {
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
        Node value = evaluateValue ? expressionEvaluator.evaluate(entry.getVal(), context) : entry.getVal();
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

    private Predicate<String> changesetPointers() {
        return new Predicate<String>() {
            @Override
            public boolean test(String pointer) {
                return "/changeset".equals(pointer) || pointer.startsWith("/changeset/");
            }
        };
    }

    private Node property(Node node, String key) {
        if (node == null || node.getProperties() == null) {
            return null;
        }
        return node.getProperties().get(key);
    }

    private String text(Node node) {
        Object value = node != null ? node.getValue() : null;
        return value instanceof String ? (String) value : null;
    }
}
