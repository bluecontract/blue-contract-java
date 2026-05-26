package blue.contract.processor.conversation.compute;

import blue.bex.api.BexEngine;
import blue.contract.processor.BlueDocumentProcessorOptions;
import blue.contract.processor.conversation.ConversationTestResources;
import blue.contract.processor.conversation.bex.BexProcessingMetrics;
import blue.contract.processor.conversation.bex.BexExpressionEnabledFields;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.workflow.SequentialWorkflowRunner;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.ProcessorStatus;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario:
 * Expression-enabled workflow fields use BEX object expressions without turning BEX into a global
 * expression language for every field.
 *
 * Main flow:
 * 1. Evaluate {@code Conversation/Update Document.changeset} when it contains BEX such as
 *    {@code $binding}.
 * 2. Evaluate {@code Conversation/Trigger Event.event} when it contains BEX.
 * 3. Preserve existing literal and legacy behavior for fields that are not BEX expressions.
 * 4. Reject invalid evaluated values, such as scalar events or non-list changesets.
 *
 * Actors and operations:
 * - The owner timeline calls {@code run}.
 * - Compute steps build prior results for {@code steps} bindings.
 * - Update Document applies evaluated patch lists.
 * - Trigger Event emits evaluated event objects.
 * - A failing JavaScript runtime verifies pure BEX paths do not call QuickJS.
 */
class BexExpressionFieldWorkflowTest {
    @Test
    void updateDocumentAppliesComputeChangesetThroughBinding() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: BuildPatch",
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendChange:",
                "              op: replace",
                "              path: /status",
                "              val: active",
                "          - $return: {}",
                "      - name: ApplyPatch",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          $binding:",
                "            name: steps",
                "            path: /BuildPatch/changeset"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("active", result.document().get("/status"));
    }

    @Test
    void updateDocumentLiteralChangesetCanContainNestedBindingValues() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: ApplyPatch",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path: /status",
                "            val:",
                "              $binding:",
                "                name: event",
                "                path: /message/request/status"));

        DocumentProcessingResult result = support.processRun(document,
                new Node().properties("status", new Node().value("active")));

        assertEquals("active", result.document().get("/status"));
    }

    @Test
    void updateDocumentSupportsDynamicPatchPath() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initialize(support.yaml(support.operationWorkflowDocumentWithStatus(String.join("\n",
                "records:",
                "  a:",
                "    status: idle"),
                String.join("\n",
                "    steps:",
                "      - name: ApplyPatch",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path:",
                "              $concat:",
                "                - /records/",
                "                - $binding:",
                "                    name: event",
                "                    path: /message/request/itemId",
                "                - /status",
                "            val: active")))).document();

        DocumentProcessingResult result = support.processRun(document,
                new Node().properties("itemId", new Node().value("a")));

        assertEquals("active", result.document().get("/records/a/status"));
    }

    @Test
    void updateDocumentRejectsInvalidBexChangesetResults() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node scalar = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: ApplyPatch",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          $document: /status"));

        DocumentProcessingResult scalarFailure = support.processRun(scalar);
        assertRuntimeFatal(scalarFailure, "must evaluate to a list");

        Node invalidOp = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: ApplyPatch",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          - op: invalid",
                "            path: /status",
                "            val:",
                "              $binding:",
                "                name: event",
                "                path: /message/request/status"));

        DocumentProcessingResult invalidOpFailure = support.processRun(invalidOp,
                new Node().properties("status", new Node().value("active")));
        assertRuntimeFatal(invalidOpFailure, "Invalid patch op");

        Node missingVal = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: ApplyPatch",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path:",
                "              $binding:",
                "                name: event",
                "                path: /message/request/path"));

        DocumentProcessingResult missingValFailure = support.processRun(missingVal,
                new Node().properties("path", new Node().value("/status")));
        assertRuntimeFatal(missingValFailure, "missing val");
    }

    @Test
    void updateDocumentRemoveAndDuplicatePathsWorkThroughBex() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node removeDocument = support.initialize(support.yaml(support.operationWorkflowDocumentWithStatus("temporary: gone",
                String.join("\n",
                "    steps:",
                "      - name: Remove",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          - op: remove",
                "            path:",
                "              $binding:",
                "                name: event",
                "                path: /message/request/path")))).document();

        DocumentProcessingResult removed = support.processRun(removeDocument,
                new Node().properties("path", new Node().value("/temporary")));
        assertFalse(removed.document().getProperties().containsKey("temporary"));

        Node duplicateDocument = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: ApplyPatch",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path: /status",
                "            val:",
                "              $binding:",
                "                name: event",
                "                path: /message/request/first",
                "          - op: replace",
                "            path: /status",
                "            val:",
                "              $binding:",
                "                name: event",
                "                path: /message/request/second"));

        DocumentProcessingResult duplicate = support.processRun(duplicateDocument,
                new Node().properties("first", new Node().value("first"))
                        .properties("second", new Node().value("second")));
        assertEquals("second", duplicate.document().get("/status"));
    }

    @Test
    void triggerEventSupportsNestedBindingAndPriorComputeEvent() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node nested = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Emit",
                "        type: Conversation/Trigger Event",
                "        event:",
                "          type: Conversation/Event",
                "          kind: Status Event",
                "          status:",
                "            $binding:",
                "              name: event",
                "              path: /message/request/status",
                "          channel:",
                "            $binding:",
                "              name: currentContract",
                "              path: /channel"));

        DocumentProcessingResult nestedResult = support.processRun(nested,
                new Node().properties("status", new Node().value("active")));
        assertEquals("Status Event", onlyEvent(nestedResult).get("/kind"));
        assertEquals("active", onlyEvent(nestedResult).get("/status"));
        assertEquals("ownerChannel", onlyEvent(nestedResult).get("/channel"));

        Node fromCompute = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: BuildEvent",
                "        type: Conversation/Compute",
                "        do:",
                "          - $return:",
                "              event:",
                "                type: Conversation/Event",
                "                kind: Built Event",
                "                status:",
                "                  $document: /status",
                "      - name: EmitEvent",
                "        type: Conversation/Trigger Event",
                "        event:",
                "          $binding:",
                "            name: steps",
                "            path: /BuildEvent/event"));

        DocumentProcessingResult fromComputeResult = support.processRun(fromCompute);
        assertEquals("Built Event", onlyEvent(fromComputeResult).get("/kind"));
        assertEquals("idle", onlyEvent(fromComputeResult).get("/status"));
    }

    @Test
    void triggerEventRejectsInvalidBexResults() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node scalar = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Emit",
                "        type: Conversation/Trigger Event",
                "        event:",
                "          $document: /status"));

        DocumentProcessingResult scalarFailure = support.processRun(scalar);
        assertRuntimeFatal(scalarFailure, "must evaluate to an object");

        Node undefined = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Emit",
                "        type: Conversation/Trigger Event",
                "        event:",
                "          $document: /missing"));

        DocumentProcessingResult undefinedFailure = support.processRun(undefined);
        assertRuntimeFatal(undefinedFailure, "undefined/null");
    }

    @Test
    void pureBexUpdateAndTriggerDoNotCallQuickJs() {
        JavaScriptRuntime failingRuntime = failingRuntime();
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                BlueDocumentProcessorOptions.builder()
                        .sequentialWorkflowRunner(SequentialWorkflowRunner.withRuntimes(
                                failingRuntime,
                                BexEngine.builder().build(),
                                100_000L,
                                100_000L))
                        .build());
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: BuildPatch",
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendChange:",
                "              op: replace",
                "              path: /status",
                "              val: active",
                "          - $return: {}",
                "      - name: ApplyPatch",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          $binding:",
                "            name: steps",
                "            path: /BuildPatch/changeset",
                "      - name: Emit",
                "        type: Conversation/Trigger Event",
                "        event:",
                "          type: Conversation/Event",
                "          kind: No QuickJS",
                "          status:",
                "            $document: /status"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("active", result.document().get("/status"));
        assertEquals("No QuickJS", onlyEvent(result).get("/kind"));
        assertEquals("active", onlyEvent(result).get("/status"));
    }

    @Test
    void directBindingFastPathsAvoidGenericBexFieldEvaluation() {
        BexProcessingMetrics metrics = new BexProcessingMetrics();
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                BlueDocumentProcessorOptions.builder()
                        .processingMetrics(metrics)
                        .build());
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Forward",
                "        type: Conversation/Trigger Event",
                "        event:",
                "          $binding:",
                "            name: event",
                "            path: /message/request",
                "      - name: BuildPatch",
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendChange:",
                "              op: replace",
                "              path: /status",
                "              val: active",
                "          - $return: {}",
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
                "                kind: Direct Event",
                "                status:",
                "                  $document: /status",
                "      - name: EmitEvent",
                "        type: Conversation/Trigger Event",
                "        event:",
                "          $binding:",
                "            name: steps",
                "            path: /BuildEvent/event"));

        DocumentProcessingResult result = support.processRun(document,
                new Node().properties("type", new Node().value("Conversation/Event"))
                        .properties("kind", new Node().value("Forwarded")));

        assertEquals("active", result.document().get("/status"));
        assertEquals(2, result.triggeredEvents().size());
        assertEquals(2L, metrics.directBexEventHits());
        assertEquals(1L, metrics.directBexChangesetHits());
        assertEquals(0L, metrics.genericBexEventEvaluations());
        assertEquals(0L, metrics.genericBexChangesetEvaluations());
        assertEquals(0L, metrics.bexFieldEvaluations());
    }

    @Test
    void existingLiteralAndLegacyExpressionPathsStillWork() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Prepare",
                "        type: Conversation/JavaScript Code",
                "        code: \"return { value: 'legacy' };\"",
                "      - name: ApplyLiteral",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path: /status",
                "            val: literal",
                "      - name: ApplyLegacy",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path: /status",
                "            val: \"${steps.Prepare.value}\"",
                "      - name: EmitLegacy",
                "        type: Conversation/Trigger Event",
                "        event:",
                "          type: Conversation/Event",
                "          kind: Existing Legacy",
                "          status: \"${document('/status')}\""));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("legacy", result.document().get("/status"));
        assertEquals("Existing Legacy", onlyEvent(result).get("/kind"));
        assertEquals("legacy", onlyEvent(result).get("/status"));
    }

    @Test
    void expressionGasLimitAppliesToUpdateDocumentAndTriggerEvent() {
        ComputeWorkflowTestSupport updateSupport = ComputeWorkflowTestSupport.create(
                BlueDocumentProcessorOptions.builder().defaultBexExpressionGasLimit(1L).build());
        Node updateDocument = updateSupport.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: ApplyPatch",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path: /status",
                "            val:",
                "              $binding:",
                "                name: event",
                "                path: /message/request/status"));

        DocumentProcessingResult updateFailure = updateSupport.processRun(updateDocument,
                new Node().properties("status", new Node().value("active")));
        assertRuntimeFatalIgnoreCase(updateFailure, "gas");

        ComputeWorkflowTestSupport triggerSupport = ComputeWorkflowTestSupport.create(
                BlueDocumentProcessorOptions.builder().defaultBexExpressionGasLimit(1L).build());
        Node triggerDocument = triggerSupport.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Emit",
                "        type: Conversation/Trigger Event",
                "        event:",
                "          type: Conversation/Event",
                "          kind:",
                "            $binding:",
                "              name: event",
                "              path: /message/request/kind"));

        DocumentProcessingResult triggerFailure = triggerSupport.processRun(triggerDocument,
                new Node().properties("kind", new Node().value("Ready")));
        assertRuntimeFatalIgnoreCase(triggerFailure, "gas");
    }

    @Test
    void fullComputeUpdateTriggerWorkflowUsesBindingWithoutQuickJs() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                BlueDocumentProcessorOptions.builder()
                        .sequentialWorkflowRunner(SequentialWorkflowRunner.withRuntimes(
                                failingRuntime(),
                                BexEngine.builder().build(),
                                100_000L,
                                100_000L))
                        .build());
        Node document = support.initialize(support.yaml(support.operationWorkflowDocumentWithContracts("",
                String.join("\n",
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
                "          - $return: {}",
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
                "            path: /BuildEvent/event")))).document();

        DocumentProcessingResult result = support.processRun(document,
                new Node().properties("status", new Node().value("active")));

        assertEquals("active", result.document().get("/status"));
        assertEquals("Status Applied", onlyEvent(result).get("/kind"));
        assertEquals("active", onlyEvent(result).get("/status"));
    }

    @Test
    void bexOutsideExpressionEnabledFieldsIsNotEvaluated() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node channelBexStep = new Node()
                .type("Conversation/Update Document")
                .properties("channel", binding("event", "/someChannel"))
                .properties("changeset", new Node().items(Collections.<Node>emptyList()));
        assertTrue(new BexExpressionEnabledFields().preservedPathsForStep(channelBexStep).isEmpty());

        Node requestSchemaDocument = support.initialize(support.yaml(String.join("\n",
                "name: BEX Request Schema Not Global",
                "status: idle",
                "contracts:",
                ConversationTestResources.simpleTimelineChannelYaml("ownerChannel", "owner", 2),
                "  run:",
                "    type: Conversation/Operation",
                "    channel: ownerChannel",
                "    request:",
                "      status:",
                "        type:",
                "          $binding:",
                "            name: event",
                "            path: /someType",
                "  runImpl:",
                "    type: Conversation/Sequential Workflow Operation",
                "    operation: run",
                "    steps:",
                "      - name: Emit",
                "        type: Conversation/Trigger Event",
                "        event:",
                "          type: Conversation/Event",
                "          kind: Should Not Run"))).document();

        DocumentProcessingResult requestSchemaResult = support.processRun(requestSchemaDocument,
                new Node().properties("status", new Node().value("active")));
        assertTrue(requestSchemaResult.triggeredEvents().isEmpty());

        Node eventMatcherDocument = support.initialize(support.yaml(support.operationWorkflowDocumentWithContracts("",
                String.join("\n",
                "    event:",
                "      timelineId:",
                "        $binding:",
                "          name: event",
                "          path: /timelineId",
                "    steps:",
                "      - name: Emit",
                "        type: Conversation/Trigger Event",
                "        event:",
                "          type: Conversation/Event",
                "          kind: Should Not Run")))).document();

        DocumentProcessingResult eventMatcherResult = support.processRun(eventMatcherDocument);
        assertTrue(eventMatcherResult.triggeredEvents().isEmpty());
    }

    @Test
    void defaultBexExpressionGasLimitMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> BlueDocumentProcessorOptions.builder().defaultBexExpressionGasLimit(0L));
        assertThrows(IllegalArgumentException.class,
                () -> BlueDocumentProcessorOptions.builder().defaultBexExpressionGasLimit(-1L));
    }

    private static JavaScriptRuntime failingRuntime() {
        return new JavaScriptRuntime() {
            @Override
            public JavaScriptEvaluationResult evaluate(JavaScriptEvaluationRequest request) {
                throw new AssertionError("QuickJS must not be called");
            }
        };
    }

    private static Node onlyEvent(DocumentProcessingResult result) {
        assertEquals(1, result.triggeredEvents().size());
        return result.triggeredEvents().get(0);
    }

    private static void assertRuntimeFatal(DocumentProcessingResult result, String expectedMessage) {
        assertEquals(ProcessorStatus.RUNTIME_FATAL, result.status(), result.failureReason());
        assertTrue(result.failureReason() != null && result.failureReason().contains(expectedMessage),
                result.failureReason());
    }

    private static void assertRuntimeFatalIgnoreCase(DocumentProcessingResult result, String expectedMessage) {
        assertEquals(ProcessorStatus.RUNTIME_FATAL, result.status(), result.failureReason());
        assertTrue(result.failureReason() != null
                        && result.failureReason().toLowerCase().contains(expectedMessage.toLowerCase()),
                result.failureReason());
    }

    private static Node binding(String name, String path) {
        return new Node().properties("$binding", new Node()
                .properties("name", new Node().value(name))
                .properties("path", new Node().value(path)));
    }
}
