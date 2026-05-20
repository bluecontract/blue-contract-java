package blue.contract.processor.conversation.bex;

import java.util.concurrent.atomic.AtomicLong;

public final class BexProcessingMetrics {
    private final AtomicLong workflowStepsExecuted = new AtomicLong();
    private final AtomicLong computeStepsExecuted = new AtomicLong();
    private final AtomicLong updateDocumentStepsExecuted = new AtomicLong();
    private final AtomicLong triggerEventStepsExecuted = new AtomicLong();
    private final AtomicLong genericBexChangesetEvaluations = new AtomicLong();
    private final AtomicLong directBexChangesetHits = new AtomicLong();
    private final AtomicLong genericBexEventEvaluations = new AtomicLong();
    private final AtomicLong directBexEventHits = new AtomicLong();
    private final AtomicLong bexFieldEvaluations = new AtomicLong();
    private final AtomicLong patchesApplied = new AtomicLong();
    private final AtomicLong eventsEmitted = new AtomicLong();
    private final AtomicLong computeProgramNormalizations = new AtomicLong();
    private final AtomicLong computeDefinitionNormalizations = new AtomicLong();

    public void incrementWorkflowStepsExecuted() {
        workflowStepsExecuted.incrementAndGet();
    }

    public void incrementComputeStepsExecuted() {
        computeStepsExecuted.incrementAndGet();
    }

    public void incrementUpdateDocumentStepsExecuted() {
        updateDocumentStepsExecuted.incrementAndGet();
    }

    public void incrementTriggerEventStepsExecuted() {
        triggerEventStepsExecuted.incrementAndGet();
    }

    public void incrementGenericBexChangesetEvaluations() {
        genericBexChangesetEvaluations.incrementAndGet();
        bexFieldEvaluations.incrementAndGet();
    }

    public void incrementDirectBexChangesetHits() {
        directBexChangesetHits.incrementAndGet();
    }

    public void incrementGenericBexEventEvaluations() {
        genericBexEventEvaluations.incrementAndGet();
        bexFieldEvaluations.incrementAndGet();
    }

    public void incrementDirectBexEventHits() {
        directBexEventHits.incrementAndGet();
    }

    public void addPatchesApplied(long count) {
        patchesApplied.addAndGet(count);
    }

    public void incrementEventsEmitted() {
        eventsEmitted.incrementAndGet();
    }

    public void incrementComputeProgramNormalizations() {
        computeProgramNormalizations.incrementAndGet();
    }

    public void incrementComputeDefinitionNormalizations() {
        computeDefinitionNormalizations.incrementAndGet();
    }

    public long workflowStepsExecuted() {
        return workflowStepsExecuted.get();
    }

    public long computeStepsExecuted() {
        return computeStepsExecuted.get();
    }

    public long updateDocumentStepsExecuted() {
        return updateDocumentStepsExecuted.get();
    }

    public long triggerEventStepsExecuted() {
        return triggerEventStepsExecuted.get();
    }

    public long genericBexChangesetEvaluations() {
        return genericBexChangesetEvaluations.get();
    }

    public long directBexChangesetHits() {
        return directBexChangesetHits.get();
    }

    public long genericBexEventEvaluations() {
        return genericBexEventEvaluations.get();
    }

    public long directBexEventHits() {
        return directBexEventHits.get();
    }

    public long bexFieldEvaluations() {
        return bexFieldEvaluations.get();
    }

    public long patchesApplied() {
        return patchesApplied.get();
    }

    public long eventsEmitted() {
        return eventsEmitted.get();
    }

    public long computeProgramNormalizations() {
        return computeProgramNormalizations.get();
    }

    public long computeDefinitionNormalizations() {
        return computeDefinitionNormalizations.get();
    }

    public Snapshot snapshot() {
        return new Snapshot(this);
    }

    public static final class Snapshot {
        public final long workflowStepsExecuted;
        public final long computeStepsExecuted;
        public final long updateDocumentStepsExecuted;
        public final long triggerEventStepsExecuted;
        public final long genericBexChangesetEvaluations;
        public final long directBexChangesetHits;
        public final long genericBexEventEvaluations;
        public final long directBexEventHits;
        public final long bexFieldEvaluations;
        public final long patchesApplied;
        public final long eventsEmitted;
        public final long computeProgramNormalizations;
        public final long computeDefinitionNormalizations;

        private Snapshot(BexProcessingMetrics metrics) {
            this.workflowStepsExecuted = metrics.workflowStepsExecuted();
            this.computeStepsExecuted = metrics.computeStepsExecuted();
            this.updateDocumentStepsExecuted = metrics.updateDocumentStepsExecuted();
            this.triggerEventStepsExecuted = metrics.triggerEventStepsExecuted();
            this.genericBexChangesetEvaluations = metrics.genericBexChangesetEvaluations();
            this.directBexChangesetHits = metrics.directBexChangesetHits();
            this.genericBexEventEvaluations = metrics.genericBexEventEvaluations();
            this.directBexEventHits = metrics.directBexEventHits();
            this.bexFieldEvaluations = metrics.bexFieldEvaluations();
            this.patchesApplied = metrics.patchesApplied();
            this.eventsEmitted = metrics.eventsEmitted();
            this.computeProgramNormalizations = metrics.computeProgramNormalizations();
            this.computeDefinitionNormalizations = metrics.computeDefinitionNormalizations();
        }
    }
}
