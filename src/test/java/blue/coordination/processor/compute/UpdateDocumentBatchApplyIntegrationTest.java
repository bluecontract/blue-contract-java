package blue.coordination.processor.compute;

import blue.coordination.processor.CoordinationProcessorOptions;
import blue.coordination.processor.bex.BexProcessingMetrics;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.ProcessorStatus;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario:
 * BEX-produced changesets flow through the language batch patch API.
 *
 * Main flow:
 * 1. Compute builds patch data, including duplicate paths where order matters.
 * 2. Compute applies returned changesets directly through batch apply.
 * 3. Additional cases prove later Compute steps see patched state, and
 *    literal Update Document changesets still use batch apply.
 *
 * Actors and operations:
 * - The owner timeline calls {@code run}.
 * - Compute creates changesets and events.
 * - Update Document remains supported for literal or separately authored patches.
 */
class UpdateDocumentBatchApplyIntegrationTest {
    @Test
    void computeChangesetUsesLanguageBatchApplyAndPreservesPatchOrder() {
        BexProcessingMetrics metrics = new BexProcessingMetrics();
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                CoordinationProcessorOptions.builder()
                        .processingMetrics(metrics)
                        .build());
        Node document = support.initialize(support.yaml(support.operationWorkflowDocumentWithStatus("count: 0",
                String.join("\n",
                        "    steps:",
                        "      - name: BuildPatch",
                        "        type: Coordination/Compute",
                        "        do:",
                        "          - $appendChange:",
                        "              op: replace",
                        "              path: /status",
                        "              val: first",
                        "          - $appendChange:",
                        "              op: replace",
                        "              path: /count",
                        "              val: 1",
                        "          - $appendChange:",
                        "              op: replace",
                        "              path: /status",
                        "              val: second",
                        "          - $return:",
                        "              changeset:",
                        "                $changeset: true",
                        "              events:",
                        "                $events: true")))).document();

        DocumentProcessingResult result = support.processRun(document);

        assertFalse(result.capabilityFailure(), result.failureReason());
        assertEquals("second", result.document().getAsText("/status"));
        assertEquals(BigInteger.ONE, result.document().get("/count"));
        assertEquals(3L, metrics.patchesApplied());
        assertEquals(1L, metrics.updateBatchPatchApplications());
        assertEquals(0L, metrics.updateIndividualPatchApplications());
        assertEquals(1L, metrics.directBexChangesetHits());
    }

    @Test
    void pureBexComputeEventUsesBatchApply() {
        BexProcessingMetrics metrics = new BexProcessingMetrics();
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                CoordinationProcessorOptions.builder()
                        .processingMetrics(metrics)
                        .build());
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: BuildPatch",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendChange:",
                "              op: replace",
                "              path: /status",
                "              val:",
                "                $binding:",
                "                  name: event",
                "                  path: /message/request/status",
                "          - $return:",
                "              changeset:",
                "                $changeset: true",
                "              events:",
                "                $events: true",
                "      - name: BuildEvent",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Status Applied",
                "              status:",
                "                $document: /status",
                "          - $return:",
                "              events:",
                "                $events: true"));

        DocumentProcessingResult result = support.processRun(document,
                new Node().properties("status", new Node().value("active")));

        assertFalse(result.capabilityFailure(), result.failureReason());
        assertEquals("active", result.document().get("/status"));
        assertEquals(1, result.triggeredEvents().size());
        assertEquals("Status Applied", result.triggeredEvents().get(0).get("/kind"));
        assertEquals("active", result.triggeredEvents().get(0).get("/status"));
        assertEquals(1L, metrics.patchesApplied());
        assertEquals(1L, metrics.updateBatchPatchApplications());
        assertEquals(0L, metrics.updateIndividualPatchApplications());
        assertEquals(1L, metrics.directBexChangesetHits());
        assertEquals(1L, metrics.eventsEmitted());
    }

    @Test
    void literalUpdateDocumentChangesetsUseBatchApply() {
        BexProcessingMetrics metrics = new BexProcessingMetrics();
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                CoordinationProcessorOptions.builder()
                        .processingMetrics(metrics)
                        .build());
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: ApplyLiteral",
                "        type: Coordination/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path: /status",
                "            val: literal",
                "      - name: ApplySecondLiteral",
                "        type: Coordination/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path: /status",
                "            val: existing"));

        DocumentProcessingResult result = support.processRun(document,
                new Node()
                        .properties("detail", new Node().value("detail"))
                        .properties("status", new Node().value("existing")));

        assertFalse(result.capabilityFailure(), result.failureReason());
        assertEquals("existing", result.document().get("/status"));
        assertEquals(2L, metrics.patchesApplied());
        assertEquals(2L, metrics.updateBatchPatchApplications());
        assertEquals(0L, metrics.updateIndividualPatchApplications());
    }

    @Test
    void updateDocumentRejectsBexOperatorsInStaticChangeset() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: ApplyPatch",
                "        type: Coordination/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path: /status",
                "            val:",
                "              $binding:",
                "                name: event",
                "                path: /message/request/status"));

        DocumentProcessingResult result = support.processRun(document,
                new Node().properties("status", new Node().value("existing")));

        assertEquals(ProcessorStatus.RUNTIME_FATAL, result.status(), result.failureReason());
        assertTrue(result.failureReason().contains("Update Document changeset must be static"));
    }
}
