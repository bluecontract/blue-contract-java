package blue.contract.processor.conversation.bex;

import blue.bex.result.BexMetrics;
import blue.language.processor.ProcessingMetricsSink;

import java.util.concurrent.atomic.AtomicLong;

public final class BexProcessingMetrics implements ProcessingMetricsSink {
    private final AtomicLong workflowStepsExecuted = new AtomicLong();
    private final AtomicLong computeStepsExecuted = new AtomicLong();
    private final AtomicLong updateDocumentStepsExecuted = new AtomicLong();
    private final AtomicLong triggerEventStepsExecuted = new AtomicLong();
    private final AtomicLong genericBexChangesetEvaluations = new AtomicLong();
    private final AtomicLong directBexChangesetHits = new AtomicLong();
    private final AtomicLong genericBexEventEvaluations = new AtomicLong();
    private final AtomicLong directBexEventHits = new AtomicLong();
    private final AtomicLong bexFieldEvaluations = new AtomicLong();
    private final AtomicLong bexSyntheticProgramMaterializations = new AtomicLong();
    private final AtomicLong patchesApplied = new AtomicLong();
    private final AtomicLong eventsEmitted = new AtomicLong();
    private final AtomicLong computeProgramNormalizations = new AtomicLong();
    private final AtomicLong computeDefinitionNormalizations = new AtomicLong();
    private final AtomicLong computeDefinitionResolveHits = new AtomicLong();
    private final AtomicLong computeDefinitionResolveMisses = new AtomicLong();
    private final AtomicLong workflowRunnerNanos = new AtomicLong();
    private final AtomicLong computeStepNanos = new AtomicLong();
    private final AtomicLong computeDefinitionResolveNanos = new AtomicLong();
    private final AtomicLong computeContextBuildNanos = new AtomicLong();
    private final AtomicLong computeProgramSourceBuildNanos = new AtomicLong();
    private final AtomicLong computeCompileExecuteNanos = new AtomicLong();
    private final AtomicLong updateStepNanos = new AtomicLong();
    private final AtomicLong updateDirectChangesetNanos = new AtomicLong();
    private final AtomicLong updatePatchConversionNanos = new AtomicLong();
    private final AtomicLong updatePatchApplyNanos = new AtomicLong();
    private final AtomicLong updateBatchPatchApplications = new AtomicLong();
    private final AtomicLong updateIndividualPatchApplications = new AtomicLong();
    private final AtomicLong triggerStepNanos = new AtomicLong();
    private final AtomicLong triggerDirectEventNanos = new AtomicLong();
    private final AtomicLong triggerEmitEventNanos = new AtomicLong();
    private final AtomicLong bexCompileNanos = new AtomicLong();
    private final AtomicLong bexExecuteNanos = new AtomicLong();
    private final AtomicLong bexCompileCacheHits = new AtomicLong();
    private final AtomicLong bexCompileCacheMisses = new AtomicLong();
    private final AtomicLong bexCompiledExecutions = new AtomicLong();
    private final AtomicLong bexNodeWriterNanos = new AtomicLong();
    private final AtomicLong directBexPatchEntryConversions = new AtomicLong();
    private final AtomicLong processDocumentNanos = new AtomicLong();
    private final AtomicLong blueProcessDocumentNanos = new AtomicLong();
    private final AtomicLong eventPreprocessNanos = new AtomicLong();
    private final AtomicLong resultSnapshotAttachNanos = new AtomicLong();
    private final AtomicLong blueIdCalculationNanos = new AtomicLong();
    private final AtomicLong processingSnapshotCacheLookupNanos = new AtomicLong();
    private final AtomicLong processingSnapshotCacheHits = new AtomicLong();
    private final AtomicLong processingSnapshotCacheMisses = new AtomicLong();
    private final AtomicLong processingSnapshotFromDocumentNanos = new AtomicLong();
    private final AtomicLong processingSnapshotFromDocumentBuilds = new AtomicLong();
    private final AtomicLong bundleLoadNanos = new AtomicLong();
    private final AtomicLong bundleLoadCacheKeyBuildNanos = new AtomicLong();
    private final AtomicLong bundleLoadActualBuildNanos = new AtomicLong();
    private final AtomicLong bundleLoadReuseNanos = new AtomicLong();
    private final AtomicLong bundleLoadCacheHits = new AtomicLong();
    private final AtomicLong bundleLoadCacheMisses = new AtomicLong();
    private final AtomicLong bundlesBuilt = new AtomicLong();
    private final AtomicLong bundlesReused = new AtomicLong();
    private final AtomicLong bundleScopeLoadAttempts = new AtomicLong();
    private final AtomicLong bundleScopeExecutionCacheHits = new AtomicLong();
    private final AtomicLong bundleScopeRefreshes = new AtomicLong();
    private final AtomicLong bundleScopeTerminationCheckNanos = new AtomicLong();
    private final AtomicLong bundleScopeResolvedLookupNanos = new AtomicLong();
    private final AtomicLong bundleScopeContractLoadNanos = new AtomicLong();
    private final AtomicLong channelDiscoveryNanos = new AtomicLong();
    private final AtomicLong channelMatchNanos = new AtomicLong();
    private final AtomicLong channelEvaluations = new AtomicLong();
    private final AtomicLong handlerDiscoveryNanos = new AtomicLong();
    private final AtomicLong handlerMatchNanos = new AtomicLong();
    private final AtomicLong handlerMatchAttempts = new AtomicLong();
    private final AtomicLong handlerExecutionNanos = new AtomicLong();
    private final AtomicLong handlersExecuted = new AtomicLong();
    private final AtomicLong triggeredEventRoutingNanos = new AtomicLong();
    private final AtomicLong triggeredEventsRouted = new AtomicLong();
    private final AtomicLong checkpointUpdateNanos = new AtomicLong();
    private final AtomicLong checkpointEnsureNanos = new AtomicLong();
    private final AtomicLong checkpointFindNanos = new AtomicLong();
    private final AtomicLong checkpointCurrentIdentityNanos = new AtomicLong();
    private final AtomicLong checkpointIsNewerNanos = new AtomicLong();
    private final AtomicLong checkpointDuplicateNanos = new AtomicLong();
    private final AtomicLong checkpointPersistNanos = new AtomicLong();
    private final AtomicLong checkpointIdentityCacheHits = new AtomicLong();
    private final AtomicLong checkpointIdentityCacheMisses = new AtomicLong();
    private final AtomicLong checkpointStoredIdentityCacheHits = new AtomicLong();
    private final AtomicLong checkpointStoredIdentityCacheMisses = new AtomicLong();
    private final AtomicLong checkpointDirectBlueIdNanos = new AtomicLong();
    private final AtomicLong checkpointContentBlueIdNanos = new AtomicLong();
    private final AtomicLong checkpointFallbackNanos = new AtomicLong();
    private final AtomicLong snapshotCommitNanos = new AtomicLong();
    private final AtomicLong postProcessingNanos = new AtomicLong();
    private final AtomicLong patchBoundaryNanos = new AtomicLong();
    private final AtomicLong patchGasNanos = new AtomicLong();
    private final AtomicLong documentUpdateRoutingNanos = new AtomicLong();
    private final AtomicLong documentUpdateEventsBuilt = new AtomicLong();
    private final AtomicLong documentUpdateEventsSkippedNoChannel = new AtomicLong();
    private final AtomicLong batchPatchPlanningNanos = new AtomicLong();
    private final AtomicLong batchPatchConformanceNanos = new AtomicLong();
    private final AtomicLong batchPatchBuildUpdatesNanos = new AtomicLong();
    private final AtomicLong batchPatchCommitNanos = new AtomicLong();
    private final AtomicLong documentUpdateBeforeMaterializations = new AtomicLong();
    private final AtomicLong documentUpdateAfterMaterializations = new AtomicLong();
    private final AtomicLong workflowDocumentViewsFromFrozen = new AtomicLong();
    private final AtomicLong workflowDocumentViewsFromDocument = new AtomicLong();
    private final AtomicLong workflowDocumentViewMisses = new AtomicLong();
    private final AtomicLong bexDocumentViewMaterializedHits = new AtomicLong();
    private final AtomicLong bexDocumentViewFrozenDirectHits = new AtomicLong();
    private final AtomicLong bexDocumentViewFrozenRootFallbackHits = new AtomicLong();
    private final AtomicLong bexDocumentViewUndefinedHits = new AtomicLong();

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

    public void incrementBexSyntheticProgramMaterializations() {
        bexSyntheticProgramMaterializations.incrementAndGet();
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

    public void incrementComputeDefinitionResolveHits() {
        computeDefinitionResolveHits.incrementAndGet();
    }

    public void incrementComputeDefinitionResolveMisses() {
        computeDefinitionResolveMisses.incrementAndGet();
    }

    public void addWorkflowRunnerNanos(long nanos) {
        workflowRunnerNanos.addAndGet(nonNegative(nanos));
    }

    public void addComputeStepNanos(long nanos) {
        computeStepNanos.addAndGet(nonNegative(nanos));
    }

    public void addComputeDefinitionResolveNanos(long nanos) {
        computeDefinitionResolveNanos.addAndGet(nonNegative(nanos));
    }

    public void addComputeContextBuildNanos(long nanos) {
        computeContextBuildNanos.addAndGet(nonNegative(nanos));
    }

    public void addComputeProgramSourceBuildNanos(long nanos) {
        computeProgramSourceBuildNanos.addAndGet(nonNegative(nanos));
    }

    public void addComputeCompileExecuteNanos(long nanos) {
        computeCompileExecuteNanos.addAndGet(nonNegative(nanos));
    }

    public void addUpdateStepNanos(long nanos) {
        updateStepNanos.addAndGet(nonNegative(nanos));
    }

    public void addUpdateDirectChangesetNanos(long nanos) {
        updateDirectChangesetNanos.addAndGet(nonNegative(nanos));
    }

    public void addUpdatePatchConversionNanos(long nanos) {
        updatePatchConversionNanos.addAndGet(nonNegative(nanos));
    }

    public void addUpdatePatchApplyNanos(long nanos) {
        updatePatchApplyNanos.addAndGet(nonNegative(nanos));
    }

    public void incrementUpdateBatchPatchApplications() {
        updateBatchPatchApplications.incrementAndGet();
    }

    public void incrementUpdateIndividualPatchApplications() {
        updateIndividualPatchApplications.incrementAndGet();
    }

    public void addTriggerStepNanos(long nanos) {
        triggerStepNanos.addAndGet(nonNegative(nanos));
    }

    public void addTriggerDirectEventNanos(long nanos) {
        triggerDirectEventNanos.addAndGet(nonNegative(nanos));
    }

    public void addTriggerEmitEventNanos(long nanos) {
        triggerEmitEventNanos.addAndGet(nonNegative(nanos));
    }

    public void addBexNodeWriterNanos(long nanos) {
        bexNodeWriterNanos.addAndGet(nonNegative(nanos));
    }

    public void incrementDirectBexPatchEntryConversions() {
        directBexPatchEntryConversions.incrementAndGet();
    }

    public void addBexMetrics(BexMetrics metrics) {
        if (metrics == null) {
            return;
        }
        bexCompileCacheHits.addAndGet(metrics.compileCacheHits());
        bexCompileCacheMisses.addAndGet(metrics.compileCacheMisses());
        bexCompiledExecutions.addAndGet(metrics.compiledExecutions());
        bexCompileNanos.addAndGet(metrics.compileNanos());
        bexExecuteNanos.addAndGet(metrics.executeNanos());
    }

    @Override
    public void addProcessDocumentNanos(long nanos) {
        processDocumentNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addBlueProcessDocumentNanos(long nanos) {
        blueProcessDocumentNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addEventPreprocessNanos(long nanos) {
        eventPreprocessNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addResultSnapshotAttachNanos(long nanos) {
        resultSnapshotAttachNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addBlueIdCalculationNanos(long nanos) {
        blueIdCalculationNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addProcessingSnapshotCacheLookupNanos(long nanos) {
        processingSnapshotCacheLookupNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void incrementProcessingSnapshotCacheHits() {
        processingSnapshotCacheHits.incrementAndGet();
    }

    @Override
    public void incrementProcessingSnapshotCacheMisses() {
        processingSnapshotCacheMisses.incrementAndGet();
    }

    @Override
    public void addProcessingSnapshotFromDocumentNanos(long nanos) {
        processingSnapshotFromDocumentNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void incrementProcessingSnapshotFromDocumentBuilds() {
        processingSnapshotFromDocumentBuilds.incrementAndGet();
    }

    @Override
    public void addBundleLoadNanos(long nanos) {
        bundleLoadNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addBundleLoadCacheKeyBuildNanos(long nanos) {
        bundleLoadCacheKeyBuildNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addBundleLoadActualBuildNanos(long nanos) {
        bundleLoadActualBuildNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addBundleLoadReuseNanos(long nanos) {
        bundleLoadReuseNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void incrementBundleLoadCacheHits() {
        bundleLoadCacheHits.incrementAndGet();
    }

    @Override
    public void incrementBundleLoadCacheMisses() {
        bundleLoadCacheMisses.incrementAndGet();
    }

    @Override
    public void incrementBundlesBuilt() {
        bundlesBuilt.incrementAndGet();
    }

    @Override
    public void incrementBundlesReused() {
        bundlesReused.incrementAndGet();
    }

    @Override
    public void incrementBundleScopeLoadAttempts() {
        bundleScopeLoadAttempts.incrementAndGet();
    }

    @Override
    public void incrementBundleScopeExecutionCacheHits() {
        bundleScopeExecutionCacheHits.incrementAndGet();
    }

    @Override
    public void incrementBundleScopeRefreshes() {
        bundleScopeRefreshes.incrementAndGet();
    }

    @Override
    public void addBundleScopeTerminationCheckNanos(long nanos) {
        bundleScopeTerminationCheckNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addBundleScopeResolvedLookupNanos(long nanos) {
        bundleScopeResolvedLookupNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addBundleScopeContractLoadNanos(long nanos) {
        bundleScopeContractLoadNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addChannelDiscoveryNanos(long nanos) {
        channelDiscoveryNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addChannelMatchNanos(long nanos) {
        channelMatchNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void incrementChannelEvaluations() {
        channelEvaluations.incrementAndGet();
    }

    @Override
    public void addHandlerDiscoveryNanos(long nanos) {
        handlerDiscoveryNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addHandlerMatchNanos(long nanos) {
        handlerMatchNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void incrementHandlerMatchAttempts() {
        handlerMatchAttempts.incrementAndGet();
    }

    @Override
    public void addHandlerExecutionNanos(long nanos) {
        handlerExecutionNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void incrementHandlersExecuted() {
        handlersExecuted.incrementAndGet();
    }

    @Override
    public void addTriggeredEventRoutingNanos(long nanos) {
        triggeredEventRoutingNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void incrementTriggeredEventsRouted() {
        triggeredEventsRouted.incrementAndGet();
    }

    @Override
    public void addCheckpointUpdateNanos(long nanos) {
        checkpointUpdateNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addCheckpointEnsureNanos(long nanos) {
        checkpointEnsureNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addCheckpointFindNanos(long nanos) {
        checkpointFindNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addCheckpointCurrentIdentityNanos(long nanos) {
        checkpointCurrentIdentityNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addCheckpointIsNewerNanos(long nanos) {
        checkpointIsNewerNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addCheckpointDuplicateNanos(long nanos) {
        checkpointDuplicateNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addCheckpointPersistNanos(long nanos) {
        checkpointPersistNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void incrementCheckpointIdentityCacheHits() {
        checkpointIdentityCacheHits.incrementAndGet();
    }

    @Override
    public void incrementCheckpointIdentityCacheMisses() {
        checkpointIdentityCacheMisses.incrementAndGet();
    }

    @Override
    public void incrementCheckpointStoredIdentityCacheHits() {
        checkpointStoredIdentityCacheHits.incrementAndGet();
    }

    @Override
    public void incrementCheckpointStoredIdentityCacheMisses() {
        checkpointStoredIdentityCacheMisses.incrementAndGet();
    }

    @Override
    public void addCheckpointDirectBlueIdNanos(long nanos) {
        checkpointDirectBlueIdNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addCheckpointContentBlueIdNanos(long nanos) {
        checkpointContentBlueIdNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addCheckpointFallbackNanos(long nanos) {
        checkpointFallbackNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addSnapshotCommitNanos(long nanos) {
        snapshotCommitNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addPostProcessingNanos(long nanos) {
        postProcessingNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addPatchBoundaryNanos(long nanos) {
        patchBoundaryNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addPatchGasNanos(long nanos) {
        patchGasNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addDocumentUpdateRoutingNanos(long nanos) {
        documentUpdateRoutingNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void incrementDocumentUpdateEventsBuilt() {
        documentUpdateEventsBuilt.incrementAndGet();
    }

    @Override
    public void incrementDocumentUpdateEventsSkippedNoChannel() {
        documentUpdateEventsSkippedNoChannel.incrementAndGet();
    }

    @Override
    public void addBatchPatchPlanningNanos(long nanos) {
        batchPatchPlanningNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addBatchPatchConformanceNanos(long nanos) {
        batchPatchConformanceNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addBatchPatchBuildUpdatesNanos(long nanos) {
        batchPatchBuildUpdatesNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void addBatchPatchCommitNanos(long nanos) {
        batchPatchCommitNanos.addAndGet(nonNegative(nanos));
    }

    @Override
    public void incrementDocumentUpdateBeforeMaterializations() {
        documentUpdateBeforeMaterializations.incrementAndGet();
    }

    @Override
    public void incrementDocumentUpdateAfterMaterializations() {
        documentUpdateAfterMaterializations.incrementAndGet();
    }

    public void incrementWorkflowDocumentViewsFromFrozen() {
        workflowDocumentViewsFromFrozen.incrementAndGet();
    }

    public void incrementWorkflowDocumentViewsFromDocument() {
        workflowDocumentViewsFromDocument.incrementAndGet();
    }

    public void incrementWorkflowDocumentViewMisses() {
        workflowDocumentViewMisses.incrementAndGet();
    }

    public void incrementBexDocumentViewMaterializedHits() {
        bexDocumentViewMaterializedHits.incrementAndGet();
    }

    public void incrementBexDocumentViewFrozenDirectHits() {
        bexDocumentViewFrozenDirectHits.incrementAndGet();
    }

    public void incrementBexDocumentViewFrozenRootFallbackHits() {
        bexDocumentViewFrozenRootFallbackHits.incrementAndGet();
    }

    public void incrementBexDocumentViewUndefinedHits() {
        bexDocumentViewUndefinedHits.incrementAndGet();
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

    public long bexSyntheticProgramMaterializations() {
        return bexSyntheticProgramMaterializations.get();
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

    public long computeDefinitionResolveHits() {
        return computeDefinitionResolveHits.get();
    }

    public long computeDefinitionResolveMisses() {
        return computeDefinitionResolveMisses.get();
    }

    public long workflowRunnerNanos() {
        return workflowRunnerNanos.get();
    }

    public long computeStepNanos() {
        return computeStepNanos.get();
    }

    public long computeDefinitionResolveNanos() {
        return computeDefinitionResolveNanos.get();
    }

    public long computeContextBuildNanos() {
        return computeContextBuildNanos.get();
    }

    public long computeProgramSourceBuildNanos() {
        return computeProgramSourceBuildNanos.get();
    }

    public long computeCompileExecuteNanos() {
        return computeCompileExecuteNanos.get();
    }

    public long updateStepNanos() {
        return updateStepNanos.get();
    }

    public long updateDirectChangesetNanos() {
        return updateDirectChangesetNanos.get();
    }

    public long updatePatchConversionNanos() {
        return updatePatchConversionNanos.get();
    }

    public long updatePatchApplyNanos() {
        return updatePatchApplyNanos.get();
    }

    public long updateBatchPatchApplications() {
        return updateBatchPatchApplications.get();
    }

    public long updateIndividualPatchApplications() {
        return updateIndividualPatchApplications.get();
    }

    public long triggerStepNanos() {
        return triggerStepNanos.get();
    }

    public long triggerDirectEventNanos() {
        return triggerDirectEventNanos.get();
    }

    public long triggerEmitEventNanos() {
        return triggerEmitEventNanos.get();
    }

    public long bexCompileNanos() {
        return bexCompileNanos.get();
    }

    public long bexExecuteNanos() {
        return bexExecuteNanos.get();
    }

    public long bexCompileCacheHits() {
        return bexCompileCacheHits.get();
    }

    public long bexCompileCacheMisses() {
        return bexCompileCacheMisses.get();
    }

    public long bexCompiledExecutions() {
        return bexCompiledExecutions.get();
    }

    public long bexNodeWriterNanos() {
        return bexNodeWriterNanos.get();
    }

    public long directBexPatchEntryConversions() {
        return directBexPatchEntryConversions.get();
    }

    public long processDocumentNanos() {
        return processDocumentNanos.get();
    }

    public long blueProcessDocumentNanos() {
        return blueProcessDocumentNanos.get();
    }

    public long eventPreprocessNanos() {
        return eventPreprocessNanos.get();
    }

    public long resultSnapshotAttachNanos() {
        return resultSnapshotAttachNanos.get();
    }

    public long blueIdCalculationNanos() {
        return blueIdCalculationNanos.get();
    }

    public long processingSnapshotCacheLookupNanos() {
        return processingSnapshotCacheLookupNanos.get();
    }

    public long processingSnapshotCacheHits() {
        return processingSnapshotCacheHits.get();
    }

    public long processingSnapshotCacheMisses() {
        return processingSnapshotCacheMisses.get();
    }

    public long processingSnapshotFromDocumentNanos() {
        return processingSnapshotFromDocumentNanos.get();
    }

    public long processingSnapshotFromDocumentBuilds() {
        return processingSnapshotFromDocumentBuilds.get();
    }

    public long bundleLoadNanos() {
        return bundleLoadNanos.get();
    }

    public long bundleLoadCacheKeyBuildNanos() {
        return bundleLoadCacheKeyBuildNanos.get();
    }

    public long bundleLoadActualBuildNanos() {
        return bundleLoadActualBuildNanos.get();
    }

    public long bundleLoadReuseNanos() {
        return bundleLoadReuseNanos.get();
    }

    public long bundleLoadCacheHits() {
        return bundleLoadCacheHits.get();
    }

    public long bundleLoadCacheMisses() {
        return bundleLoadCacheMisses.get();
    }

    public long bundlesBuilt() {
        return bundlesBuilt.get();
    }

    public long bundlesReused() {
        return bundlesReused.get();
    }

    public long bundleScopeLoadAttempts() {
        return bundleScopeLoadAttempts.get();
    }

    public long bundleScopeExecutionCacheHits() {
        return bundleScopeExecutionCacheHits.get();
    }

    public long bundleScopeRefreshes() {
        return bundleScopeRefreshes.get();
    }

    public long bundleScopeTerminationCheckNanos() {
        return bundleScopeTerminationCheckNanos.get();
    }

    public long bundleScopeResolvedLookupNanos() {
        return bundleScopeResolvedLookupNanos.get();
    }

    public long bundleScopeContractLoadNanos() {
        return bundleScopeContractLoadNanos.get();
    }

    public long channelDiscoveryNanos() {
        return channelDiscoveryNanos.get();
    }

    public long channelMatchNanos() {
        return channelMatchNanos.get();
    }

    public long channelEvaluations() {
        return channelEvaluations.get();
    }

    public long handlerDiscoveryNanos() {
        return handlerDiscoveryNanos.get();
    }

    public long handlerMatchNanos() {
        return handlerMatchNanos.get();
    }

    public long handlerMatchAttempts() {
        return handlerMatchAttempts.get();
    }

    public long handlerExecutionNanos() {
        return handlerExecutionNanos.get();
    }

    public long handlersExecuted() {
        return handlersExecuted.get();
    }

    public long triggeredEventRoutingNanos() {
        return triggeredEventRoutingNanos.get();
    }

    public long triggeredEventsRouted() {
        return triggeredEventsRouted.get();
    }

    public long checkpointUpdateNanos() {
        return checkpointUpdateNanos.get();
    }

    public long checkpointEnsureNanos() {
        return checkpointEnsureNanos.get();
    }

    public long checkpointFindNanos() {
        return checkpointFindNanos.get();
    }

    public long checkpointCurrentIdentityNanos() {
        return checkpointCurrentIdentityNanos.get();
    }

    public long checkpointIsNewerNanos() {
        return checkpointIsNewerNanos.get();
    }

    public long checkpointDuplicateNanos() {
        return checkpointDuplicateNanos.get();
    }

    public long checkpointPersistNanos() {
        return checkpointPersistNanos.get();
    }

    public long checkpointIdentityCacheHits() {
        return checkpointIdentityCacheHits.get();
    }

    public long checkpointIdentityCacheMisses() {
        return checkpointIdentityCacheMisses.get();
    }

    public long checkpointStoredIdentityCacheHits() {
        return checkpointStoredIdentityCacheHits.get();
    }

    public long checkpointStoredIdentityCacheMisses() {
        return checkpointStoredIdentityCacheMisses.get();
    }

    public long checkpointDirectBlueIdNanos() {
        return checkpointDirectBlueIdNanos.get();
    }

    public long checkpointContentBlueIdNanos() {
        return checkpointContentBlueIdNanos.get();
    }

    public long checkpointFallbackNanos() {
        return checkpointFallbackNanos.get();
    }

    public long snapshotCommitNanos() {
        return snapshotCommitNanos.get();
    }

    public long postProcessingNanos() {
        return postProcessingNanos.get();
    }

    public long patchBoundaryNanos() {
        return patchBoundaryNanos.get();
    }

    public long patchGasNanos() {
        return patchGasNanos.get();
    }

    public long documentUpdateRoutingNanos() {
        return documentUpdateRoutingNanos.get();
    }

    public long documentUpdateEventsBuilt() {
        return documentUpdateEventsBuilt.get();
    }

    public long documentUpdateEventsSkippedNoChannel() {
        return documentUpdateEventsSkippedNoChannel.get();
    }

    public long batchPatchPlanningNanos() {
        return batchPatchPlanningNanos.get();
    }

    public long batchPatchConformanceNanos() {
        return batchPatchConformanceNanos.get();
    }

    public long batchPatchBuildUpdatesNanos() {
        return batchPatchBuildUpdatesNanos.get();
    }

    public long batchPatchCommitNanos() {
        return batchPatchCommitNanos.get();
    }

    public long documentUpdateBeforeMaterializations() {
        return documentUpdateBeforeMaterializations.get();
    }

    public long documentUpdateAfterMaterializations() {
        return documentUpdateAfterMaterializations.get();
    }

    public long workflowDocumentViewsFromFrozen() {
        return workflowDocumentViewsFromFrozen.get();
    }

    public long workflowDocumentViewsFromDocument() {
        return workflowDocumentViewsFromDocument.get();
    }

    public long workflowDocumentViewMisses() {
        return workflowDocumentViewMisses.get();
    }

    public long bexDocumentViewMaterializedHits() {
        return bexDocumentViewMaterializedHits.get();
    }

    public long bexDocumentViewFrozenDirectHits() {
        return bexDocumentViewFrozenDirectHits.get();
    }

    public long bexDocumentViewFrozenRootFallbackHits() {
        return bexDocumentViewFrozenRootFallbackHits.get();
    }

    public long bexDocumentViewUndefinedHits() {
        return bexDocumentViewUndefinedHits.get();
    }

    public Snapshot snapshot() {
        return new Snapshot(this);
    }

    private static long nonNegative(long nanos) {
        return nanos > 0L ? nanos : 0L;
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
        public final long bexSyntheticProgramMaterializations;
        public final long patchesApplied;
        public final long eventsEmitted;
        public final long computeProgramNormalizations;
        public final long computeDefinitionNormalizations;
        public final long computeDefinitionResolveHits;
        public final long computeDefinitionResolveMisses;
        public final long workflowRunnerNanos;
        public final long computeStepNanos;
        public final long computeDefinitionResolveNanos;
        public final long computeContextBuildNanos;
        public final long computeProgramSourceBuildNanos;
        public final long computeCompileExecuteNanos;
        public final long updateStepNanos;
        public final long updateDirectChangesetNanos;
        public final long updatePatchConversionNanos;
        public final long updatePatchApplyNanos;
        public final long updateBatchPatchApplications;
        public final long updateIndividualPatchApplications;
        public final long triggerStepNanos;
        public final long triggerDirectEventNanos;
        public final long triggerEmitEventNanos;
        public final long bexCompileNanos;
        public final long bexExecuteNanos;
        public final long bexCompileCacheHits;
        public final long bexCompileCacheMisses;
        public final long bexCompiledExecutions;
        public final long bexNodeWriterNanos;
        public final long directBexPatchEntryConversions;
        public final long processDocumentNanos;
        public final long blueProcessDocumentNanos;
        public final long eventPreprocessNanos;
        public final long resultSnapshotAttachNanos;
        public final long blueIdCalculationNanos;
        public final long processingSnapshotCacheLookupNanos;
        public final long processingSnapshotCacheHits;
        public final long processingSnapshotCacheMisses;
        public final long processingSnapshotFromDocumentNanos;
        public final long processingSnapshotFromDocumentBuilds;
        public final long bundleLoadNanos;
        public final long bundleLoadCacheKeyBuildNanos;
        public final long bundleLoadActualBuildNanos;
        public final long bundleLoadReuseNanos;
        public final long bundleLoadCacheHits;
        public final long bundleLoadCacheMisses;
        public final long bundlesBuilt;
        public final long bundlesReused;
        public final long bundleScopeLoadAttempts;
        public final long bundleScopeExecutionCacheHits;
        public final long bundleScopeRefreshes;
        public final long bundleScopeTerminationCheckNanos;
        public final long bundleScopeResolvedLookupNanos;
        public final long bundleScopeContractLoadNanos;
        public final long channelDiscoveryNanos;
        public final long channelMatchNanos;
        public final long channelEvaluations;
        public final long handlerDiscoveryNanos;
        public final long handlerMatchNanos;
        public final long handlerMatchAttempts;
        public final long handlerExecutionNanos;
        public final long handlersExecuted;
        public final long triggeredEventRoutingNanos;
        public final long triggeredEventsRouted;
        public final long checkpointUpdateNanos;
        public final long checkpointEnsureNanos;
        public final long checkpointFindNanos;
        public final long checkpointCurrentIdentityNanos;
        public final long checkpointIsNewerNanos;
        public final long checkpointDuplicateNanos;
        public final long checkpointPersistNanos;
        public final long checkpointIdentityCacheHits;
        public final long checkpointIdentityCacheMisses;
        public final long checkpointStoredIdentityCacheHits;
        public final long checkpointStoredIdentityCacheMisses;
        public final long checkpointDirectBlueIdNanos;
        public final long checkpointContentBlueIdNanos;
        public final long checkpointFallbackNanos;
        public final long snapshotCommitNanos;
        public final long postProcessingNanos;
        public final long patchBoundaryNanos;
        public final long patchGasNanos;
        public final long documentUpdateRoutingNanos;
        public final long documentUpdateEventsBuilt;
        public final long documentUpdateEventsSkippedNoChannel;
        public final long batchPatchPlanningNanos;
        public final long batchPatchConformanceNanos;
        public final long batchPatchBuildUpdatesNanos;
        public final long batchPatchCommitNanos;
        public final long documentUpdateBeforeMaterializations;
        public final long documentUpdateAfterMaterializations;
        public final long workflowDocumentViewsFromFrozen;
        public final long workflowDocumentViewsFromDocument;
        public final long workflowDocumentViewMisses;
        public final long bexDocumentViewMaterializedHits;
        public final long bexDocumentViewFrozenDirectHits;
        public final long bexDocumentViewFrozenRootFallbackHits;
        public final long bexDocumentViewUndefinedHits;

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
            this.bexSyntheticProgramMaterializations = metrics.bexSyntheticProgramMaterializations();
            this.patchesApplied = metrics.patchesApplied();
            this.eventsEmitted = metrics.eventsEmitted();
            this.computeProgramNormalizations = metrics.computeProgramNormalizations();
            this.computeDefinitionNormalizations = metrics.computeDefinitionNormalizations();
            this.computeDefinitionResolveHits = metrics.computeDefinitionResolveHits();
            this.computeDefinitionResolveMisses = metrics.computeDefinitionResolveMisses();
            this.workflowRunnerNanos = metrics.workflowRunnerNanos();
            this.computeStepNanos = metrics.computeStepNanos();
            this.computeDefinitionResolveNanos = metrics.computeDefinitionResolveNanos();
            this.computeContextBuildNanos = metrics.computeContextBuildNanos();
            this.computeProgramSourceBuildNanos = metrics.computeProgramSourceBuildNanos();
            this.computeCompileExecuteNanos = metrics.computeCompileExecuteNanos();
            this.updateStepNanos = metrics.updateStepNanos();
            this.updateDirectChangesetNanos = metrics.updateDirectChangesetNanos();
            this.updatePatchConversionNanos = metrics.updatePatchConversionNanos();
            this.updatePatchApplyNanos = metrics.updatePatchApplyNanos();
            this.updateBatchPatchApplications = metrics.updateBatchPatchApplications();
            this.updateIndividualPatchApplications = metrics.updateIndividualPatchApplications();
            this.triggerStepNanos = metrics.triggerStepNanos();
            this.triggerDirectEventNanos = metrics.triggerDirectEventNanos();
            this.triggerEmitEventNanos = metrics.triggerEmitEventNanos();
            this.bexCompileNanos = metrics.bexCompileNanos();
            this.bexExecuteNanos = metrics.bexExecuteNanos();
            this.bexCompileCacheHits = metrics.bexCompileCacheHits();
            this.bexCompileCacheMisses = metrics.bexCompileCacheMisses();
            this.bexCompiledExecutions = metrics.bexCompiledExecutions();
            this.bexNodeWriterNanos = metrics.bexNodeWriterNanos();
            this.directBexPatchEntryConversions = metrics.directBexPatchEntryConversions();
            this.processDocumentNanos = metrics.processDocumentNanos();
            this.blueProcessDocumentNanos = metrics.blueProcessDocumentNanos();
            this.eventPreprocessNanos = metrics.eventPreprocessNanos();
            this.resultSnapshotAttachNanos = metrics.resultSnapshotAttachNanos();
            this.blueIdCalculationNanos = metrics.blueIdCalculationNanos();
            this.processingSnapshotCacheLookupNanos = metrics.processingSnapshotCacheLookupNanos();
            this.processingSnapshotCacheHits = metrics.processingSnapshotCacheHits();
            this.processingSnapshotCacheMisses = metrics.processingSnapshotCacheMisses();
            this.processingSnapshotFromDocumentNanos = metrics.processingSnapshotFromDocumentNanos();
            this.processingSnapshotFromDocumentBuilds = metrics.processingSnapshotFromDocumentBuilds();
            this.bundleLoadNanos = metrics.bundleLoadNanos();
            this.bundleLoadCacheKeyBuildNanos = metrics.bundleLoadCacheKeyBuildNanos();
            this.bundleLoadActualBuildNanos = metrics.bundleLoadActualBuildNanos();
            this.bundleLoadReuseNanos = metrics.bundleLoadReuseNanos();
            this.bundleLoadCacheHits = metrics.bundleLoadCacheHits();
            this.bundleLoadCacheMisses = metrics.bundleLoadCacheMisses();
            this.bundlesBuilt = metrics.bundlesBuilt();
            this.bundlesReused = metrics.bundlesReused();
            this.bundleScopeLoadAttempts = metrics.bundleScopeLoadAttempts();
            this.bundleScopeExecutionCacheHits = metrics.bundleScopeExecutionCacheHits();
            this.bundleScopeRefreshes = metrics.bundleScopeRefreshes();
            this.bundleScopeTerminationCheckNanos = metrics.bundleScopeTerminationCheckNanos();
            this.bundleScopeResolvedLookupNanos = metrics.bundleScopeResolvedLookupNanos();
            this.bundleScopeContractLoadNanos = metrics.bundleScopeContractLoadNanos();
            this.channelDiscoveryNanos = metrics.channelDiscoveryNanos();
            this.channelMatchNanos = metrics.channelMatchNanos();
            this.channelEvaluations = metrics.channelEvaluations();
            this.handlerDiscoveryNanos = metrics.handlerDiscoveryNanos();
            this.handlerMatchNanos = metrics.handlerMatchNanos();
            this.handlerMatchAttempts = metrics.handlerMatchAttempts();
            this.handlerExecutionNanos = metrics.handlerExecutionNanos();
            this.handlersExecuted = metrics.handlersExecuted();
            this.triggeredEventRoutingNanos = metrics.triggeredEventRoutingNanos();
            this.triggeredEventsRouted = metrics.triggeredEventsRouted();
            this.checkpointUpdateNanos = metrics.checkpointUpdateNanos();
            this.checkpointEnsureNanos = metrics.checkpointEnsureNanos();
            this.checkpointFindNanos = metrics.checkpointFindNanos();
            this.checkpointCurrentIdentityNanos = metrics.checkpointCurrentIdentityNanos();
            this.checkpointIsNewerNanos = metrics.checkpointIsNewerNanos();
            this.checkpointDuplicateNanos = metrics.checkpointDuplicateNanos();
            this.checkpointPersistNanos = metrics.checkpointPersistNanos();
            this.checkpointIdentityCacheHits = metrics.checkpointIdentityCacheHits();
            this.checkpointIdentityCacheMisses = metrics.checkpointIdentityCacheMisses();
            this.checkpointStoredIdentityCacheHits = metrics.checkpointStoredIdentityCacheHits();
            this.checkpointStoredIdentityCacheMisses = metrics.checkpointStoredIdentityCacheMisses();
            this.checkpointDirectBlueIdNanos = metrics.checkpointDirectBlueIdNanos();
            this.checkpointContentBlueIdNanos = metrics.checkpointContentBlueIdNanos();
            this.checkpointFallbackNanos = metrics.checkpointFallbackNanos();
            this.snapshotCommitNanos = metrics.snapshotCommitNanos();
            this.postProcessingNanos = metrics.postProcessingNanos();
            this.patchBoundaryNanos = metrics.patchBoundaryNanos();
            this.patchGasNanos = metrics.patchGasNanos();
            this.documentUpdateRoutingNanos = metrics.documentUpdateRoutingNanos();
            this.documentUpdateEventsBuilt = metrics.documentUpdateEventsBuilt();
            this.documentUpdateEventsSkippedNoChannel = metrics.documentUpdateEventsSkippedNoChannel();
            this.batchPatchPlanningNanos = metrics.batchPatchPlanningNanos();
            this.batchPatchConformanceNanos = metrics.batchPatchConformanceNanos();
            this.batchPatchBuildUpdatesNanos = metrics.batchPatchBuildUpdatesNanos();
            this.batchPatchCommitNanos = metrics.batchPatchCommitNanos();
            this.documentUpdateBeforeMaterializations = metrics.documentUpdateBeforeMaterializations();
            this.documentUpdateAfterMaterializations = metrics.documentUpdateAfterMaterializations();
            this.workflowDocumentViewsFromFrozen = metrics.workflowDocumentViewsFromFrozen();
            this.workflowDocumentViewsFromDocument = metrics.workflowDocumentViewsFromDocument();
            this.workflowDocumentViewMisses = metrics.workflowDocumentViewMisses();
            this.bexDocumentViewMaterializedHits = metrics.bexDocumentViewMaterializedHits();
            this.bexDocumentViewFrozenDirectHits = metrics.bexDocumentViewFrozenDirectHits();
            this.bexDocumentViewFrozenRootFallbackHits = metrics.bexDocumentViewFrozenRootFallbackHits();
            this.bexDocumentViewUndefinedHits = metrics.bexDocumentViewUndefinedHits();
        }
    }
}
