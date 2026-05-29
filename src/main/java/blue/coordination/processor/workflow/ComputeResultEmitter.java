package blue.coordination.processor.workflow;

import blue.bex.result.BexChangeset;
import blue.bex.result.BexExecutionResult;
import blue.bex.result.BexPatchEntry;
import blue.bex.value.BexNodeWriter;
import blue.bex.value.BexValue;
import blue.bex.value.BexValues;
import blue.coordination.processor.bex.BexProcessingMetrics;
import blue.language.model.Node;
import blue.language.processor.WorkingDocument;
import blue.language.processor.model.JsonPatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class ComputeResultEmitter {
    private final BexProcessingMetrics metrics;

    ComputeResultEmitter() {
        this(null);
    }

    ComputeResultEmitter(BexProcessingMetrics metrics) {
        this.metrics = metrics;
    }

    int applyChangeset(BexExecutionResult result, StepExecutionContext context) {
        List<JsonPatch> patches = changesetPatches(result, context);
        if (patches == null || patches.isEmpty()) {
            return 0;
        }
        applyPatches(patches, context);
        return patches.size();
    }

    boolean hasReturnedChangeset(BexExecutionResult result) {
        BexValue changeset = result.value() != null ? result.value().get("changeset") : BexValues.undefined();
        return !changeset.isUndefined() && !changeset.isNull();
    }

    int emit(BexExecutionResult result, StepExecutionContext context) {
        BexValue events = result.value() != null ? result.value().get("events") : BexValues.undefined();
        if (events.isUndefined() || events.isNull()) {
            events = result.events().asValue();
        }
        if (events.isUndefined() || events.isNull()) {
            return 0;
        }
        if (!events.isList()) {
            context.processorContext().throwFatal("Compute result events must be a list");
            return 0;
        }
        if (events.size() == 0) {
            return 0;
        }
        int emitted = 0;
        for (int i = 0; i < events.size(); i++) {
            BexValue event = events.get(String.valueOf(i));
            if (event.isUndefined() || event.isNull()) {
                context.processorContext().throwFatal("Compute result events cannot contain undefined/null entries");
                return emitted;
            }
            if (!event.isObject()) {
                context.processorContext().throwFatal("Compute result events must contain object entries");
                return emitted;
            }
            Node eventNode = BexNodeWriter.toNode(event);
            context.processorContext().emitEvent(eventNode);
            emitted++;
        }
        return emitted;
    }

    private List<JsonPatch> changesetPatches(BexExecutionResult result, StepExecutionContext context) {
        BexValue changeset = result.value() != null ? result.value().get("changeset") : BexValues.undefined();
        BexChangeset accumulated = result.changeset();
        if (changeset.isUndefined() || changeset.isNull()) {
            return patchesFromBexChangeset(accumulated, context);
        }
        if (!changeset.isList()) {
            context.processorContext().throwFatal("Compute result changeset must be a list");
            return null;
        }
        if (changeset.size() == 0) {
            return null;
        }
        if (isAccumulatedChangesetValue(changeset, accumulated)) {
            return patchesFromBexChangeset(accumulated, context);
        }
        List<JsonPatch> patches = new ArrayList<JsonPatch>(changeset.size());
        for (int i = 0; i < changeset.size(); i++) {
            BexValue item = changeset.get(String.valueOf(i));
            WorkflowPatchEntry entry = patchEntry(item, i, context);
            if (entry == null) {
                return null;
            }
            patches.add(toPatch(entry, context));
        }
        return patches;
    }

    private List<JsonPatch> patchesFromBexChangeset(BexChangeset changeset, StepExecutionContext context) {
        if (changeset == null || changeset.entries().isEmpty()) {
            return null;
        }
        if (metrics != null) {
            metrics.incrementDirectBexChangesetHits();
        }
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

    private WorkflowPatchEntry patchEntry(BexValue item, int index, StepExecutionContext context) {
        if (item == null || item.isUndefined() || item.isNull() || !item.isObject()) {
            context.processorContext().throwFatal("Compute result changeset entry " + index + " must be an object");
            return null;
        }
        String op = textValue(item.get("op"));
        String path = textValue(item.get("path"));
        if (!"add".equals(op) && !"replace".equals(op) && !"remove".equals(op)) {
            context.processorContext().throwFatal("Invalid patch op in Compute result changeset: " + op);
            return null;
        }
        if (path == null || path.trim().isEmpty()) {
            context.processorContext().throwFatal("Compute result changeset entry " + index + " missing path");
            return null;
        }
        Node nodeValue = null;
        if (!"remove".equals(op)) {
            BexValue val = item.get("val");
            if (val.isUndefined()) {
                context.processorContext().throwFatal("Compute result changeset entry " + index + " missing val");
                return null;
            }
            long writerStart = System.nanoTime();
            nodeValue = BexNodeWriter.toNode(val);
            if (metrics != null) {
                metrics.addBexNodeWriterNanos(System.nanoTime() - writerStart);
            }
        }
        return new WorkflowPatchEntry(op, path, nodeValue);
    }

    private JsonPatch toPatch(WorkflowPatchEntry entry, StepExecutionContext context) {
        String normalizedOp = entry.op().trim().toLowerCase();
        String path = context.processorContext().resolvePointer(entry.path());
        if ("remove".equals(normalizedOp)) {
            return JsonPatch.remove(path);
        }
        Node value = entry.val();
        if (value == null) {
            context.processorContext().throwFatal("Compute result patch value is required for operation: " + entry.op());
            return null;
        }
        if ("add".equals(normalizedOp)) {
            return JsonPatch.add(path, value);
        }
        if ("replace".equals(normalizedOp)) {
            return JsonPatch.replace(path, value);
        }
        context.processorContext().throwFatal("Unsupported Compute result patch operation: " + entry.op());
        return null;
    }

    private JsonPatch toPatch(BexPatchEntry entry, StepExecutionContext context) {
        String normalizedOp = entry.op().trim().toLowerCase();
        String path = context.processorContext().resolvePointer(entry.authoredPath());
        if ("remove".equals(normalizedOp)) {
            return JsonPatch.remove(path);
        }
        if (entry.val() == null || entry.val().isUndefined()) {
            context.processorContext().throwFatal("Compute result patch value is required for operation: " + entry.op());
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
        context.processorContext().throwFatal("Unsupported Compute result patch operation: " + entry.op());
        return null;
    }

    private void applyPatches(List<JsonPatch> patches, StepExecutionContext context) {
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

    private boolean isAccumulatedChangesetValue(BexValue value, BexChangeset changeset) {
        if (value == null || !value.isList() || changeset == null) {
            return false;
        }
        if (value.size() != changeset.entries().size()) {
            return false;
        }
        for (int i = 0; i < value.size(); i++) {
            BexValue item = value.get(String.valueOf(i));
            BexPatchEntry entry = changeset.entries().get(i);
            if (item == null || !item.isObject()) {
                return false;
            }
            if (!entry.op().equals(textValue(item.get("op")))) {
                return false;
            }
            String path = textValue(item.get("path"));
            if (!entry.authoredPath().equals(path) && !entry.absolutePath().equals(path)) {
                return false;
            }
            BexValue val = item.get("val");
            if (entry.val() == null || entry.val().isUndefined()) {
                if (val != null && !val.isUndefined() && !val.isNull()) {
                    return false;
                }
            } else if (val == null || val.isUndefined() || !Objects.equals(entry.val().toSimple(), val.toSimple())) {
                return false;
            }
        }
        return true;
    }

    private String textValue(BexValue value) {
        if (value == null || value.isUndefined() || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
