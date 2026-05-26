package blue.contract.processor.conversation.compute;

import blue.contract.processor.BlueDocumentProcessorOptions;
import blue.contract.processor.conversation.bex.BexProcessingMetrics;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.language.snapshot.FrozenNode;
import blue.language.snapshot.ResolvedSnapshot;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Scenario:
 * A production-style counter document is initialized once, stored as canonical data, then reloaded for
 * every incoming event before being processed and stored again.
 *
 * Main flow:
 * 1. Initialize the BEX counter document and serialize the canonical result.
 * 2. Run 100 independent {@code increment} operation requests, each adding 1 to {@code /counter}.
 * 3. Before each increment, deserialize the previously stored canonical document and load a snapshot.
 * 4. After each increment, serialize the new canonical document for the next iteration.
 *
 * Actors and operations:
 * - The owner timeline calls {@code increment}.
 * - {@code Conversation/Compute} builds the changeset.
 * - {@code Conversation/Update Document} applies the changeset through the BEX {@code $binding}
 *   steps path using batch patch application.
 */
class BexCounterPersistenceRoundTripTest {
    private static final int ITERATIONS = 100;
    private static final String COUNTER_RESOURCE = "conversation/compute/bex-counter-persistence.yaml";

    @Test
    void serializedCanonicalDocumentCanBeReloadedAndProcessedAcrossOneHundredBexIncrements() {
        BexProcessingMetrics metrics = new BexProcessingMetrics();
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                BlueDocumentProcessorOptions.builder()
                        .processingMetrics(metrics)
                        .build());

        long start = System.nanoTime();

        long initializeStart = System.nanoTime();
        DocumentProcessingResult initialized = support.initialize(support.yamlResource(COUNTER_RESOURCE));
        long initializeNanos = System.nanoTime() - initializeStart;
        assertFalse(initialized.capabilityFailure(), initialized.failureReason());
        assertNotNull(initialized.snapshot());

        long initialSerializeStart = System.nanoTime();
        String storedCanonicalJson = serializeCanonical(support, initialized);
        long initialSerializeNanos = System.nanoTime() - initialSerializeStart;
        String storedBlueId = initialized.blueId();
        assertNotNull(storedBlueId);

        long totalProcessNanos = 0L;
        long totalDeserializeAndLoadSnapshotNanos = 0L;
        long totalSerializeNanos = 0L;

        for (int i = 1; i <= ITERATIONS; i++) {
            long loadStart = System.nanoTime();
            ResolvedSnapshot snapshot = deserializeCanonicalAndLoadSnapshot(support, storedCanonicalJson);
            totalDeserializeAndLoadSnapshotNanos += System.nanoTime() - loadStart;
            assertNotNull(snapshot.blueId(), "stored snapshot should load at iteration " + i);
            storedBlueId = snapshot.blueId();

            long processStart = System.nanoTime();
            DocumentProcessingResult result = support.blue.processDocument(snapshot,
                    support.operationRequest("increment", new Node().value(1)));
            totalProcessNanos += System.nanoTime() - processStart;

            assertFalse(result.capabilityFailure(), result.failureReason());
            assertNotNull(result.snapshot(), "iteration " + i + " should return a snapshot");
            assertEquals(BigInteger.valueOf(i), result.resolvedDocument().get("/counter"));

            long serializeStart = System.nanoTime();
            storedCanonicalJson = serializeCanonical(support, result);
            storedBlueId = result.blueId();
            totalSerializeNanos += System.nanoTime() - serializeStart;
        }

        long totalNanos = System.nanoTime() - start;
        ResolvedSnapshot finalSnapshot = deserializeCanonicalAndLoadSnapshot(support, storedCanonicalJson);
        assertEquals(BigInteger.valueOf(ITERATIONS), finalSnapshot.resolvedNodeAt("/counter").getValue());
        assertEquals(ITERATIONS, metrics.updateBatchPatchApplications());
        assertEquals(ITERATIONS, metrics.directBexChangesetHits());
        assertEquals(0L, metrics.updateIndividualPatchApplications());

        System.out.printf("BEX counter persistence round trip - iterations=%d, finalBlueId=%s, totalMs=%.3f, "
                        + "initializeMs=%.3f, initialSerializeMs=%.3f, deserializeLoadSnapshotMs=%.3f, "
                        + "processMs=%.3f, serializeMs=%.3f, "
                        + "batchPatchApplications=%d, bundleCacheHits=%d, bundleCacheMisses=%d%n",
                ITERATIONS,
                storedBlueId,
                nanosToMs(totalNanos),
                nanosToMs(initializeNanos),
                nanosToMs(initialSerializeNanos),
                nanosToMs(totalDeserializeAndLoadSnapshotNanos),
                nanosToMs(totalProcessNanos),
                nanosToMs(totalSerializeNanos),
                metrics.updateBatchPatchApplications(),
                metrics.bundleLoadCacheHits(),
                metrics.bundleLoadCacheMisses());
    }

    private static String serializeCanonical(ComputeWorkflowTestSupport support, DocumentProcessingResult result) {
        assertNotNull(result.canonicalDocument());
        return support.blue.nodeToJson(result.canonicalDocument());
    }

    private static ResolvedSnapshot deserializeCanonicalAndLoadSnapshot(ComputeWorkflowTestSupport support,
            String storedCanonicalJson) {
        Node storedCanonical = support.blue.parseSourceJson(storedCanonicalJson);
        FrozenNode canonicalRoot = FrozenNode.fromUncheckedCanonicalNode(storedCanonical);
        return new ResolvedSnapshot(canonicalRoot,
                FrozenNode.fromResolvedNode(storedCanonical),
                canonicalRoot.blueId());
    }

    private static double nanosToMs(long nanos) {
        return nanos / 1_000_000.0;
    }
}
