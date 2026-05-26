package blue.contract.processor.conversation.compute;

import blue.contract.processor.BlueDocumentProcessorOptions;
import blue.contract.processor.conversation.bex.BexProcessingMetrics;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Scenario:
 * Update Document consumes BEX-produced changesets through the language batch patch API.
 *
 * Main flow:
 * 1. Compute builds patch data, including duplicate paths where order matters.
 * 2. Update Document reads that changeset through canonical BEX {@code $binding} to
 *    {@code steps/BuildPatch/changeset}.
 * 3. The executor converts the changeset to patches and calls batch apply once.
 * 4. Additional cases prove pure BEX Compute -> Update Document -> Trigger Event does not call QuickJS,
 *    and that literal, generic BEX, and legacy changeset forms still route through batch apply.
 *
 * Actors and operations:
 * - The owner timeline calls {@code run}.
 * - Compute creates changesets and events.
 * - Update Document performs the only document mutation.
 * - Trigger Event emits the post-update event in the pure BEX path.
 */
class UpdateDocumentBatchApplyIntegrationTest {
    @Test
    void directBexChangesetUsesLanguageBatchApplyAndPreservesPatchOrder() {
        BexProcessingMetrics metrics = new BexProcessingMetrics();
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                BlueDocumentProcessorOptions.builder()
                        .processingMetrics(metrics)
                        .build());
        Node document = support.initialize(support.yaml(support.operationWorkflowDocumentWithStatus("count: 0",
                String.join("\n",
                        "    steps:",
                        "      - name: BuildPatch",
                        "        type: Conversation/Compute",
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
                        "                $events: true",
                        "      - name: ApplyPatch",
                        "        type: Conversation/Update Document",
                        "        changeset:",
                        "          $binding:",
                        "            name: steps",
                        "            path: /BuildPatch/changeset")))).document();

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
    void pureBexComputeUpdateTriggerUsesBatchApplyWithoutQuickJs() {
        BexProcessingMetrics metrics = new BexProcessingMetrics();
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                BlueDocumentProcessorOptions.builder()
                        .javaScriptRuntime(failingRuntime())
                        .processingMetrics(metrics)
                        .build());
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: BuildPatch",
                "        type: Conversation/Compute",
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
                "      - name: ApplyPatch",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          $binding:",
                "            name: steps",
                "            path: /BuildPatch/changeset",
                "      - name: BuildEvent",
                "        type: Conversation/Compute",
                "        do:",
                "          - $return:",
                "              event:",
                "                type: Conversation/Event",
                "                kind: Status Applied",
                "                status:",
                "                  $document: /status",
                "      - name: EmitEvent",
                "        type: Conversation/Trigger Event",
                "        event:",
                "          $binding:",
                "            name: steps",
                "            path: /BuildEvent/event"));

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
        assertEquals(1L, metrics.directBexEventHits());
    }

    @Test
    void literalGenericBexAndLegacyChangesetsAllUseBatchApply() {
        BexProcessingMetrics metrics = new BexProcessingMetrics();
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                BlueDocumentProcessorOptions.builder()
                        .processingMetrics(metrics)
                        .build());
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: ApplyLiteral",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path: /status",
                "            val: literal",
                "      - name: ApplyGenericBex",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path: /status",
                "            val:",
                "              $binding:",
                "                name: event",
                "                path: /message/request/status",
                "      - name: PrepareLegacy",
                "        type: Conversation/JavaScript Code",
                "        code: \"return { value: 'legacy' };\"",
                "      - name: ApplyLegacy",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path: /status",
                "            val: \"${steps.PrepareLegacy.value}\""));

        DocumentProcessingResult result = support.processRun(document,
                new Node().properties("status", new Node().value("generic")));

        assertFalse(result.capabilityFailure(), result.failureReason());
        assertEquals("legacy", result.document().get("/status"));
        assertEquals(3L, metrics.patchesApplied());
        assertEquals(3L, metrics.updateBatchPatchApplications());
        assertEquals(0L, metrics.updateIndividualPatchApplications());
        assertEquals(1L, metrics.genericBexChangesetEvaluations());
    }

    private static JavaScriptRuntime failingRuntime() {
        return new JavaScriptRuntime() {
            @Override
            public JavaScriptEvaluationResult evaluate(JavaScriptEvaluationRequest request) {
                throw new AssertionError("QuickJS must not be called");
            }
        };
    }
}
