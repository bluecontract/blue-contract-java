package blue.coordination.processor.workflow;

import blue.coordination.processor.bex.BexProcessingMetrics;
import blue.language.model.Node;
import blue.language.processor.WorkingDocument;
import blue.language.processor.model.JsonPatch;
import blue.language.snapshot.FrozenNode;
import blue.repo.coordination.SequentialWorkflowStep;
import blue.repo.coordination.UpdateDocument;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class UpdateDocumentStepExecutor implements WorkflowStepExecutor<UpdateDocument> {
    private final BexProcessingMetrics metrics;

    public UpdateDocumentStepExecutor() {
        this(null);
    }

    public UpdateDocumentStepExecutor(BexProcessingMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public boolean supports(SequentialWorkflowStep step) {
        return step instanceof UpdateDocument;
    }

    @Override
    public WorkflowStepResult execute(UpdateDocument step, StepExecutionContext context) {
        long stepStart = System.nanoTime();
        try {
            if (metrics != null) {
                metrics.incrementUpdateDocumentStepsExecuted();
            }
            FrozenNode rawFrozenChangeset = FrozenNodeUtil.property(context.stepFrozenNode(), "changeset");
            if (StaticPayloadValidator.rejectBexOperators(rawFrozenChangeset,
                    context,
                    "Update Document changeset")) {
                return WorkflowStepResult.none();
            }
            if (rawFrozenChangeset != null
                    && rawFrozenChangeset.getItems() == null
                    && step.getChangeset() == null) {
                context.processorContext().throwFatal("Update Document changeset must be a static patch list");
                return WorkflowStepResult.none();
            }
            List<WorkflowPatchEntry> changeset = literalChangeset(step, context);
            if (changeset.isEmpty()) {
                return WorkflowStepResult.none();
            }
            long conversionStart = System.nanoTime();
            List<JsonPatch> patches = new ArrayList<JsonPatch>(changeset.size());
            for (WorkflowPatchEntry entry : changeset) {
                patches.add(toPatch(entry, context));
            }
            if (metrics != null) {
                metrics.addUpdatePatchConversionNanos(System.nanoTime() - conversionStart);
            }
            applyPatches(patches, context);
            return WorkflowStepResult.none();
        } finally {
            if (metrics != null) {
                metrics.addUpdateStepNanos(System.nanoTime() - stepStart);
            }
        }
    }

    private List<WorkflowPatchEntry> literalChangeset(UpdateDocument step, StepExecutionContext context) {
        if (step == null || step.getChangeset() == null) {
            return java.util.Collections.emptyList();
        }
        List<?> rawChangeset = step.getChangeset();
        List<WorkflowPatchEntry> entries = new ArrayList<WorkflowPatchEntry>(rawChangeset.size());
        for (int i = 0; i < rawChangeset.size(); i++) {
            entries.add(literalPatchEntry(rawChangeset.get(i), i, context));
        }
        return entries;
    }

    private WorkflowPatchEntry literalPatchEntry(Object item, int index, StepExecutionContext context) {
        if (item == null) {
            return null;
        }
        if (item instanceof WorkflowPatchEntry) {
            return (WorkflowPatchEntry) item;
        }
        if (item instanceof Node) {
            return literalPatchEntry((Node) item, index, context);
        }
        try {
            String op = (String) invokeNoArg(item, "getOp");
            String path = (String) invokeNoArg(item, "getPath");
            Node val = (Node) invokeNoArg(item, "getVal");
            return new WorkflowPatchEntry(op, path, val);
        } catch (ReflectiveOperationException ex) {
            context.processorContext().throwFatal("Update Document changeset entry " + index
                    + " cannot be read as a patch entry: " + ex.getMessage());
            return null;
        } catch (ClassCastException ex) {
            context.processorContext().throwFatal("Update Document changeset entry " + index
                    + " has invalid patch entry field types");
            return null;
        }
    }

    private WorkflowPatchEntry literalPatchEntry(Node item, int index, StepExecutionContext context) {
        if (item.getProperties() == null) {
            context.processorContext().throwFatal("Update Document changeset entry " + index
                    + " must be a static patch object");
            return null;
        }
        String op = stringProperty(item, "op", index, context);
        String path = stringProperty(item, "path", index, context);
        Node val = item.getProperties().get("val");
        return new WorkflowPatchEntry(op, path, val != null ? val.clone() : null);
    }

    private String stringProperty(Node item, String key, int index, StepExecutionContext context) {
        Node property = item.getProperties().get(key);
        Object value = property != null ? property.getValue() : null;
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            context.processorContext().throwFatal("Update Document changeset entry " + index
                    + " field '" + key + "' must be text");
            return null;
        }
        return (String) value;
    }

    private Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private JsonPatch toPatch(WorkflowPatchEntry entry, StepExecutionContext context) {
        if (entry == null) {
            context.processorContext().throwFatal("Update Document changeset contains a null patch entry");
            return null;
        }
        String op = entry.op();
        String path = entry.path();
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
        Node value = entry.val();
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

    private void applyPatches(List<JsonPatch> patches, StepExecutionContext context) {
        if (patches == null || patches.isEmpty()) {
            return;
        }
        long applyStart = System.nanoTime();
        boolean applied = false;
        try {
            WorkingDocument.Preview preview = context.advanceWorkingDocument(patches);
            context.processorContext().applyPreviewedPatches(patches, preview);
            applied = true;
        } finally {
            if (metrics != null) {
                metrics.addUpdatePatchApplyNanos(System.nanoTime() - applyStart);
                if (applied) {
                    metrics.addPatchesApplied(patches.size());
                    metrics.incrementUpdateBatchPatchApplications();
                }
            }
        }
    }
}
