package blue.coordination.processor.bex;

import blue.bex.api.BexDocumentView;
import blue.bex.value.BexValue;
import blue.bex.value.BexValues;
import blue.coordination.processor.workflow.StepExecutionContext;
import blue.language.processor.ProcessorExecutionContext;
import blue.language.snapshot.FrozenNode;
import blue.language.utils.JsonPointer;

import java.util.Objects;

/**
 * BEX document view that resolves authored pointers against the active processor scope.
 */
final class ScopedProcessorExecutionContextBexDocumentView implements BexDocumentView {
    private final StepExecutionContext stepContext;
    private final ProcessorExecutionContext context;
    private final BexProcessingMetrics metrics;

    ScopedProcessorExecutionContextBexDocumentView(StepExecutionContext context) {
        this(context, null);
    }

    ScopedProcessorExecutionContextBexDocumentView(StepExecutionContext context,
                                                   BexProcessingMetrics metrics) {
        this.stepContext = Objects.requireNonNull(context, "context");
        this.context = context.processorContext();
        this.metrics = metrics;
    }

    @Override
    public String resolvePointer(String authoredPointer) {
        return context.resolvePointer(authoredPointer);
    }

    @Override
    public BexValue canonicalAt(String pointer) {
        return frozenAt(context.resolvePointer(pointer), true);
    }

    @Override
    public BexValue resolvedAt(String pointer) {
        return frozenAt(context.resolvePointer(pointer), false);
    }

    @Override
    public String currentScopePath() {
        return context.scopePath();
    }

    private BexValue frozenAt(String absolutePointer, boolean canonical) {
        FrozenNode viewed = canonical
                ? stepContext.workingCanonicalAt(absolutePointer)
                : stepContext.workingResolvedAt(absolutePointer);
        if (viewed != null) {
            if (metrics != null) {
                metrics.incrementBexDocumentViewFrozenDirectHits();
            }
            return BexValues.frozen(viewed);
        }
        FrozenNode selected = canonical
                ? context.canonicalFrozenAt(absolutePointer)
                : context.resolvedFrozenAt(absolutePointer);
        if (selected != null) {
            if (metrics != null) {
                metrics.incrementBexDocumentViewFrozenDirectHits();
            }
            return BexValues.frozen(selected);
        }
        FrozenNode root = canonical
                ? stepContext.workingDocument().canonicalRoot()
                : stepContext.workingDocument().resolvedRoot();
        if (root != null) {
            if (metrics != null) {
                metrics.incrementBexDocumentViewFrozenRootFallbackHits();
            }
            return BexValues.frozen(root).at(JsonPointer.split(absolutePointer));
        }
        if (metrics != null) {
            metrics.incrementBexDocumentViewUndefinedHits();
        }
        return BexValues.undefined();
    }
}
