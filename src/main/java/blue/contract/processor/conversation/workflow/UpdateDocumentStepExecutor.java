package blue.contract.processor.conversation.workflow;

import blue.bex.BexException;
import blue.bex.result.BexChangeset;
import blue.bex.result.BexExecutionResult;
import blue.bex.result.BexPatchEntry;
import blue.bex.value.BexNodeWriter;
import blue.bex.value.BexValue;
import blue.contract.processor.conversation.bex.BexBindingReference;
import blue.contract.processor.conversation.bex.BexExpressionDetector;
import blue.contract.processor.conversation.bex.BexFieldEvaluator;
import blue.contract.processor.conversation.bex.BexProcessingMetrics;
import blue.contract.processor.conversation.expression.ExpressionEvaluator;
import blue.contract.processor.conversation.expression.QuickJsExpressionResolver;
import blue.language.model.Node;
import blue.language.processor.WorkingDocument;
import blue.language.processor.model.JsonPatch;
import blue.language.snapshot.FrozenNode;
import blue.language.utils.JsonPointer;
import blue.repo.conversation.SequentialWorkflowStep;
import blue.repo.conversation.UpdateDocument;
import blue.repo.core.JsonPatchEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class UpdateDocumentStepExecutor implements WorkflowStepExecutor<UpdateDocument> {
    private final QuickJsExpressionResolver resolver;
    private final ExpressionEvaluator expressionEvaluator;
    private final BexExpressionDetector bexDetector;
    private final BexFieldEvaluator bexFieldEvaluator;
    private final long bexExpressionGasLimit;
    private final BexProcessingMetrics metrics;

    public UpdateDocumentStepExecutor(QuickJsExpressionResolver resolver) {
        this(resolver, null, null, 100_000L, null);
    }

    public UpdateDocumentStepExecutor(QuickJsExpressionResolver resolver,
                                      BexExpressionDetector bexDetector,
                                      BexFieldEvaluator bexFieldEvaluator,
                                      long bexExpressionGasLimit) {
        this(resolver, bexDetector, bexFieldEvaluator, bexExpressionGasLimit, null);
    }

    public UpdateDocumentStepExecutor(QuickJsExpressionResolver resolver,
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
        this.expressionEvaluator = null;
        this.bexDetector = bexDetector;
        this.bexFieldEvaluator = bexFieldEvaluator;
        this.bexExpressionGasLimit = bexExpressionGasLimit;
        this.metrics = metrics;
    }

    public UpdateDocumentStepExecutor(ExpressionEvaluator expressionEvaluator) {
        if (expressionEvaluator == null) {
            throw new IllegalArgumentException("expressionEvaluator must not be null");
        }
        this.resolver = null;
        this.expressionEvaluator = expressionEvaluator;
        this.bexDetector = null;
        this.bexFieldEvaluator = null;
        this.bexExpressionGasLimit = 100_000L;
        this.metrics = null;
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
            List<JsonPatch> directPatches = directStepChangesetPatches(rawFrozenChangeset, context);
            if (directPatches != null) {
                applyPatches(directPatches, context);
                return WorkflowStepResult.none();
            }
            List<JsonPatchEntry> changeset = changeset(step, context, rawFrozenChangeset);
            if (changeset.isEmpty()) {
                return WorkflowStepResult.none();
            }
            long conversionStart = System.nanoTime();
            List<JsonPatch> patches = new ArrayList<JsonPatch>(changeset.size());
            for (JsonPatchEntry entry : changeset) {
                patches.add(toPatch(entry, context, resolver == null));
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

    private List<JsonPatchEntry> changeset(UpdateDocument step,
                                           StepExecutionContext context,
                                           FrozenNode rawFrozenChangeset) {
        if (bexDetector != null && bexFieldEvaluator != null && bexDetector.containsBex(rawFrozenChangeset)) {
            return bexChangeset(rawFrozenChangeset, context);
        }
        if (resolver == null || context.stepNode() == null) {
            return legacyChangeset(step);
        }
        Node resolvedStep = resolver.resolve(context.stepNode(),
                context,
                changesetPointers(),
                path -> true);
        return extractChangeset(resolvedStep, context);
    }

    private List<JsonPatchEntry> bexChangeset(FrozenNode rawChangeset, StepExecutionContext context) {
        try {
            if (metrics != null) {
                metrics.incrementGenericBexChangesetEvaluations();
            }
            BexValue value = bexFieldEvaluator.evaluateField(rawChangeset, context, bexExpressionGasLimit);
            return patchEntriesFromBexValue(value, context);
        } catch (BexException ex) {
            context.processorContext().throwFatal("Update Document changeset expression failed: " + ex.getMessage());
            return Collections.emptyList();
        } catch (RuntimeException ex) {
            context.processorContext().throwFatal("Update Document changeset expression failed: " + ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<JsonPatch> directStepChangesetPatches(FrozenNode rawChangeset, StepExecutionContext context) {
        long start = System.nanoTime();
        try {
            BexBindingReference reference = BexBindingReference.parse(rawChangeset);
            if (reference == null || !"steps".equals(reference.name())) {
                return null;
            }
            StepPath stepPath = StepPath.parse(reference.path());
            if (stepPath == null || !"changeset".equals(stepPath.field) || stepPath.remainingPath != null) {
                return null;
            }
            Object result = context.stepResults().get(stepPath.stepName);
            if (!(result instanceof BexExecutionResult)) {
                return null;
            }
            BexChangeset changeset = ((BexExecutionResult) result).changeset();
            if (changeset == null || changeset.entries().isEmpty()) {
                return null;
            }
            if (metrics != null) {
                metrics.incrementDirectBexChangesetHits();
            }
            return patchesFromBexChangeset(changeset, context);
        } finally {
            if (metrics != null) {
                metrics.addUpdateDirectChangesetNanos(System.nanoTime() - start);
            }
        }
    }

    private List<JsonPatch> patchesFromBexChangeset(BexChangeset changeset, StepExecutionContext context) {
        long conversionStart = System.nanoTime();
        try {
            List<JsonPatch> patches = new ArrayList<JsonPatch>(changeset.entries().size());
            for (BexPatchEntry entry : changeset.entries()) {
                patches.add(toPatch(entry, context));
                if (metrics != null) {
                    metrics.incrementDirectBexPatchEntryConversions();
                }
            }
            return patches;
        } finally {
            if (metrics != null) {
                metrics.addUpdatePatchConversionNanos(System.nanoTime() - conversionStart);
            }
        }
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

    private List<JsonPatchEntry> patchEntriesFromBexValue(BexValue value, StepExecutionContext context) {
        if (value == null || !value.isList()) {
            context.processorContext().throwFatal("Update Document changeset expression must evaluate to a list");
            return Collections.emptyList();
        }
        List<JsonPatchEntry> entries = new ArrayList<JsonPatchEntry>();
        for (int i = 0; i < value.size(); i++) {
            BexValue item = value.get(String.valueOf(i));
            if (item.isUndefined() || item.isNull() || !item.isObject()) {
                context.processorContext().throwFatal("Update Document changeset entry " + i + " must be an object");
                return Collections.emptyList();
            }
            String op = textValue(item.get("op"));
            String path = textValue(item.get("path"));
            if (!"add".equals(op) && !"replace".equals(op) && !"remove".equals(op)) {
                context.processorContext().throwFatal("Invalid patch op in Update Document changeset: " + op);
                return Collections.emptyList();
            }
            if (path == null || path.trim().isEmpty()) {
                context.processorContext().throwFatal("Patch entry " + i + " missing path");
                return Collections.emptyList();
            }
            JsonPatchEntry entry = new JsonPatchEntry()
                    .op(op)
                    .path(path);
            if (!"remove".equals(op)) {
                BexValue val = item.get("val");
                if (val.isUndefined()) {
                    context.processorContext().throwFatal("Patch entry " + i + " missing val");
                    return Collections.emptyList();
                }
                long writerStart = System.nanoTime();
                entry.val(BexNodeWriter.toNode(val));
                if (metrics != null) {
                    metrics.addBexNodeWriterNanos(System.nanoTime() - writerStart);
                }
            }
            entries.add(entry);
        }
        return entries;
    }

    private String textValue(BexValue value) {
        if (value == null || value.isUndefined() || value.isNull()) {
            return null;
        }
        return value.asText();
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

    private JsonPatch toPatch(BexPatchEntry entry, StepExecutionContext context) {
        if (entry == null) {
            context.processorContext().throwFatal("Update Document changeset contains a null patch entry");
            return null;
        }
        String op = entry.op();
        String path = context.processorContext().resolvePointer(entry.authoredPath());
        if (op == null || op.trim().isEmpty()) {
            context.processorContext().throwFatal("Update Document patch operation is required");
            return null;
        }
        if (path == null || path.trim().isEmpty()) {
            context.processorContext().throwFatal("Update Document patch path is required");
            return null;
        }
        String normalizedOp = op.trim().toLowerCase();
        if ("remove".equals(normalizedOp)) {
            return JsonPatch.remove(path);
        }
        if (entry.val() == null || entry.val().isUndefined()) {
            context.processorContext().throwFatal("Update Document patch value is required for operation: " + op);
            return null;
        }
        long writerStart = System.nanoTime();
        Node value = BexNodeWriter.toNode(entry.val());
        if (metrics != null) {
            metrics.addBexNodeWriterNanos(System.nanoTime() - writerStart);
        }
        if ("add".equals(normalizedOp)) {
            return JsonPatch.add(path, value);
        }
        if ("replace".equals(normalizedOp)) {
            return JsonPatch.replace(path, value);
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

    private static final class StepPath {
        private final String stepName;
        private final String field;
        private final String remainingPath;

        private StepPath(String stepName, String field, String remainingPath) {
            this.stepName = stepName;
            this.field = field;
            this.remainingPath = remainingPath;
        }

        private static StepPath parse(String path) {
            List<String> segments = JsonPointer.split(path);
            if (segments.size() < 2) {
                return null;
            }
            String remaining = segments.size() > 2
                    ? JsonPointer.toPointer(segments.subList(2, segments.size()))
                    : null;
            return new StepPath(segments.get(0), segments.get(1), remaining);
        }
    }
}
