package blue.coordination.processor.compute;

import blue.bex.api.BexEngine;
import blue.bex.api.BexMetricsSink;
import blue.bex.result.BexMetrics;
import blue.coordination.processor.CoordinationProcessorOptions;
import blue.coordination.processor.CoordinationTestResources;
import blue.coordination.processor.workflow.SequentialWorkflowRunner;
import blue.coordination.processor.workflow.StepExecutionContext;
import blue.coordination.processor.workflow.WorkflowStepExecutor;
import blue.coordination.processor.workflow.WorkflowStepResult;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.ProcessorStatus;
import blue.repo.coordination.Compute;
import blue.repo.coordination.SequentialWorkflowStep;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario:
 * Primary {@code Coordination/Compute} behavior is verified with direct patch and event effects.
 *
 * Main flow:
 * 1. Execute inline Compute programs and Compute Definition backed programs.
 * 2. Prove Compute can read {@code $document}, {@code $event}, {@code $steps}, and
 *    {@code $currentContract}.
 * 3. Prove Compute can apply returned changesets, emit events, return step results, and consume gas.
 * 4. Prove returned changesets remain readable as step result data after being applied.
 * 5. Keep Trigger Event and literal Update Document compatibility intact.
 *
 * Actors and operations:
 * - The owner timeline calls {@code run}.
 * - Compute steps build patches, data, and events.
 * - Later Compute steps read prior named step results.
 * - Compatibility cases ensure existing non-BEX workflow executors still work.
 */
class ComputeWorkflowExecutionTest {
    @Test
    void inlineComputeEmitsEventAndDoesNotMutateDocument() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Compute Event",
                "          - $return: {}"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("idle", result.document().get("/status"));
        assertEquals(1, result.triggeredEvents().size());
        assertEquals("Compute Event", result.triggeredEvents().get(0).get("/kind"));
    }

    @Test
    void inlineComputeResultIsReadableByLaterComputeViaSteps() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        do:",
                "          - $return:",
                "              approved: true",
                "              reason: ok",
                "      - name: ReadPrior",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Prior Result",
                "              approved:",
                "                $steps: Build.approved",
                "              reason:",
                "                $steps: Build.reason",
                "          - $return: {}"));

        DocumentProcessingResult result = support.processRun(document);
        Node event = onlyEvent(result);

        assertEquals("Prior Result", event.get("/kind"));
        assertEquals(Boolean.TRUE, event.get("/approved"));
        assertEquals("ok", event.get("/reason"));
    }

    @Test
    void emitEventsFalseSuppressesComputedEvents() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        emitEvents: false",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Should Not Emit",
                "          - $return: {}"));

        DocumentProcessingResult result = support.processRun(document);

        assertTrue(result.triggeredEvents().isEmpty());
    }

    @Test
    void emitEventsFalseStillExportsStepResult() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        emitEvents: false",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Should Not Emit",
                "          - $return:",
                "              approved: true",
                "      - name: Read",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Exported Result",
                "              approved:",
                "                $steps: Build.approved",
                "          - $return: {}"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("Exported Result", onlyEvent(result).get("/kind"));
        assertEquals(Boolean.TRUE, onlyEvent(result).get("/approved"));
    }

    @Test
    void returnResultFalseSuppressesStepResult() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        returnResult: false",
                "        do:",
                "          - $return:",
                "              approved: true",
                "      - name: ReadPrior",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Missing Prior",
                "              approved:",
                "                $coalesce:",
                "                  - $steps: Build.approved",
                "                  - missing",
                "          - $return: {}"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("missing", onlyEvent(result).get("/approved"));
    }

    @Test
    void returnResultFalseStillAllowsEventEmission() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        returnResult: false",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Event Still Emits",
                "          - $return:",
                "              approved: true"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("Event Still Emits", onlyEvent(result).get("/kind"));
    }

    @Test
    void unnamedComputeStepExportsAsStepIndexKey() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - type: Coordination/Compute",
                "        do:",
                "          - $return:",
                "              value: abc",
                "      - name: Read",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind:",
                "                $steps: Step1.value",
                "          - $return: {}"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("abc", onlyEvent(result).get("/kind"));
    }

    @Test
    void computeChangesetAppliesAndRemainsStepData() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: BuildPatch",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendChange:",
                "              op: replace",
                "              path: /status",
                "              val: active",
                "          - $return: {}",
                "      - name: VerifyPatchData",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Patch Data",
                "              patchPath:",
                "                $steps:",
                "                  step: BuildPatch",
                "                  path: /changeset/0/path",
                "              patchValue:",
                "                $steps:",
                "                  step: BuildPatch",
                "                  path: /changeset/0/val",
                "          - $return: {}"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("active", result.document().get("/status"));
        assertEquals("/status", onlyEvent(result).get("/patchPath"));
        assertEquals("active", onlyEvent(result).get("/patchValue"));
    }

    @Test
    void explicitEmptyChangesetSuppressesAccumulatedChanges() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: BuildPatch",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendChange:",
                "              op: replace",
                "              path: /status",
                "              val: active",
                "          - $return:",
                "              changeset: []"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("idle", result.document().get("/status"));
    }

    @Test
    void returnResultFalseStillAppliesChangeset() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: BuildPatch",
                "        type: Coordination/Compute",
                "        returnResult: false",
                "        do:",
                "          - $appendChange:",
                "              op: replace",
                "              path: /status",
                "              val: active",
                "          - $return:",
                "              ignored: true"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("active", result.document().get("/status"));
    }

    @Test
    void inlineExprComputeExportsScalarResult() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: ReadStatus",
                "        type: Coordination/Compute",
                "        expr:",
                "          $document: /status",
                "      - name: EmitStatus",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Status",
                "              status:",
                "                $steps: ReadStatus",
                "          - $return: {}"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("idle", onlyEvent(result).get("/status"));
    }

    @Test
    void computeReadsEventDocumentAndCurrentContract() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Inputs",
                "              request:",
                "                $event: /message/request",
                "              status:",
                "                $document: /status",
                "              channel:",
                "                $currentContract: /channel",
                "          - $return: {}"));

        DocumentProcessingResult result = support.processRun(document, new Node().value("hello"));
        Node event = onlyEvent(result);

        assertEquals("hello", event.get("/request"));
        assertEquals("idle", event.get("/status"));
        assertEquals("ownerChannel", event.get("/channel"));
    }

    @Test
    void currentContractChannelBindingPreservesAuthoredChannel() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initialize(support.yaml(String.join("\n",
                "name: Compute Authored Channel Test",
                "status: idle",
                "contracts:",
                CoordinationTestResources.simpleTimelineChannelYaml("manualChannel", "owner", 2),
                "  run:",
                "    type: Coordination/Operation",
                "    channel: manualChannel",
                "  runImpl:",
                "    type: Coordination/Sequential Workflow Operation",
                "    operation: run",
                "    channel: manualChannel",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Authored Channel",
                "              channel:",
                "                $currentContract: /channel",
                "          - $return: {}"))).document();

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("manualChannel", onlyEvent(result).get("/channel"));
    }

    @Test
    void computeDefinitionCanBeReferencedBySiblingContractKey() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initialize(support.yaml(support.operationWorkflowDocumentWithContracts(String.join("\n",
                "  computeLogic:",
                "    type: Coordination/Compute Definition",
                "    constants:",
                "      kind: From Definition",
                "    functions:",
                "      build:",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind:",
                "                $const: kind",
                "          - $return: {}"),
                String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        definition: computeLogic",
                "        entry: build")))).document();

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("From Definition", onlyEvent(result).get("/kind"));
    }

    @Test
    void computeDefinitionCanBeReferencedByAbsolutePointer() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initialize(support.yaml(support.operationWorkflowDocumentWithContracts(String.join("\n",
                "  computeLogic:",
                "    type: Coordination/Compute Definition",
                "    functions:",
                "      build:",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Absolute Definition",
                "          - $return: {}"),
                String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        definition: /contracts/computeLogic",
                "        entry: build")))).document();

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("Absolute Definition", onlyEvent(result).get("/kind"));
    }

    @Test
    void inlineObjectComputeDefinitionWorks() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        definition:",
                "          constants:",
                "            kind: Inline Definition",
                "          functions:",
                "            build:",
                "              do:",
                "                - $appendEvent:",
                "                    type: Coordination/Event",
                "                    kind:",
                "                      $const: kind",
                "                - $return: {}",
                "        entry: build"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("Inline Definition", onlyEvent(result).get("/kind"));
    }

    @Test
    void computeDefinitionMarkerDoesNotExecuteByItself() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initialize(support.yaml(support.operationWorkflowDocumentWithContracts(String.join("\n",
                "  computeLogic:",
                "    type: Coordination/Compute Definition",
                "    functions:",
                "      build:",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Should Not Happen",
                "          - $return: {}"),
                String.join("\n",
                "    steps: []")))).document();

        DocumentProcessingResult result = support.processRun(document);

        assertTrue(result.triggeredEvents().isEmpty());
    }

    @Test
    void missingDefinitionFailsClosed() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        definition: missingCompute",
                "        entry: build"));

        DocumentProcessingResult result = support.processRun(document);

        assertRuntimeFatal(result, "Compute definition not found");
    }

    @Test
    void missingEntryFailsClosed() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initialize(support.yaml(support.operationWorkflowDocumentWithContracts(String.join("\n",
                "  computeLogic:",
                "    type: Coordination/Compute Definition",
                "    functions:",
                "      build:",
                "        do:",
                "          - $return: {}"),
                String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        definition: computeLogic",
                "        entry: missing")))).document();

        DocumentProcessingResult result = support.processRun(document);

        assertRuntimeFatal(result, "Unknown entry function");
    }

    @Test
    void stepConstantsOverrideDefinitionConstants() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initialize(support.yaml(support.operationWorkflowDocumentWithContracts(String.join("\n",
                "  computeLogic:",
                "    type: Coordination/Compute Definition",
                "    constants:",
                "      kind: From Definition",
                "    functions:",
                "      build:",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind:",
                "                $const: kind",
                "          - $return: {}"),
                String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        definition: computeLogic",
                "        entry: build",
                "        constants:",
                "          kind: From Step")))).document();

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("From Step", onlyEvent(result).get("/kind"));
    }

    @Test
    void definitionReferenceEscapesJsonPointerSegments() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initialize(support.yaml(support.operationWorkflowDocumentWithContracts(String.join("\n",
                "  \"compute/logic~v1\":",
                "    type: Coordination/Compute Definition",
                "    functions:",
                "      build:",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Escaped Definition",
                "          - $return: {}"),
                String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        definition: compute/logic~v1",
                "        entry: build")))).document();

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("Escaped Definition", onlyEvent(result).get("/kind"));
    }

    @Test
    void localFunctionsWorkWithoutDefinition() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        entry: build",
                "        functions:",
                "          build:",
                "            do:",
                "              - $appendEvent:",
                "                  type: Coordination/Event",
                "                  kind: Local Function",
                "              - $return: {}"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("Local Function", onlyEvent(result).get("/kind"));
    }

    @Test
    void gasLimitFailureAndDefaultGasLimitFromOptionsFailClosed() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        gasLimit: 1",
                "        do:",
                "          - $return:",
                "              ok: true"));

        DocumentProcessingResult explicit = support.processRun(document);
        assertRuntimeFatalIgnoreCase(explicit, "gas");

        ComputeWorkflowTestSupport lowDefault = ComputeWorkflowTestSupport.create(
                CoordinationProcessorOptions.builder().defaultComputeGasLimit(1L).build());
        Node lowDefaultDocument = lowDefault.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        do:",
                "          - $return:",
                "              ok: true"));
        DocumentProcessingResult defaultFailure = lowDefault.processRun(lowDefaultDocument);
        assertRuntimeFatalIgnoreCase(defaultFailure, "gas");

        ComputeWorkflowTestSupport normalDefault = ComputeWorkflowTestSupport.create(
                CoordinationProcessorOptions.builder().defaultComputeGasLimit(100_000L).build());
        Node normalDocument = normalDefault.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        do:",
                "          - $return:",
                "              ok: true"));

        assertFalse(normalDefault.processRun(normalDocument).capabilityFailure());
    }

    @Test
    void defaultComputeGasLimitMustBePositive() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                () -> CoordinationProcessorOptions.builder().defaultComputeGasLimit(0L));
        assertTrue(zero.getMessage().contains("defaultComputeGasLimit must be positive"));

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> CoordinationProcessorOptions.builder().defaultComputeGasLimit(-1L));
        assertTrue(negative.getMessage().contains("defaultComputeGasLimit must be positive"));
    }

    @Test
    void explicitResultEventsAndAccumulatorEventsAreEmitted() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Explicit",
                "        type: Coordination/Compute",
                "        do:",
                "          - $return:",
                "              events:",
                "                - type: Coordination/Event",
                "                  kind: Explicit Events",
                "              changeset: []",
                "      - name: Accumulator",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Accumulator Event",
                "          - $return:",
                "              approved: true"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals(2, result.triggeredEvents().size());
        assertEquals("Explicit Events", result.triggeredEvents().get(0).get("/kind"));
        assertEquals("Accumulator Event", result.triggeredEvents().get(1).get("/kind"));
    }

    @Test
    void invalidEventsFieldFailsClosed() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        do:",
                "          - $return:",
                "              events: not-a-list"));

        DocumentProcessingResult result = support.processRun(document);

        assertRuntimeFatal(result, "Compute result events must be a list");
    }

    @Test
    void invalidChangesetFieldFailsClosed() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        do:",
                "          - $return:",
                "              changeset: not-a-list"));

        DocumentProcessingResult result = support.processRun(document);

        assertRuntimeFatal(result, "Compute result changeset must be a list");
    }

    @Test
    void scalarChangesetEntriesFailClosed() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        do:",
                "          - $return:",
                "              changeset:",
                "                - hello"));

        DocumentProcessingResult result = support.processRun(document);

        assertRuntimeFatal(result, "Compute result changeset entry 0 must be an object");
    }

    @Test
    void scalarEventEntriesFailClosed() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        do:",
                "          - $return:",
                "              events:",
                "                - hello"));

        DocumentProcessingResult result = support.processRun(document);

        assertRuntimeFatal(result, "Compute result events must contain object entries");
    }

    @Test
    void nullEventEntriesFailClosed() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        do:",
                "          - $return:",
                "              events:",
                "                - null"));

        DocumentProcessingResult result = support.processRun(document);

        assertRuntimeFatal(result, "Compute result events must contain object entries");
    }

    @Test
    void pureComputeWorkflowRunsWithBexOnlyRunner() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                CoordinationProcessorOptions.builder()
                        .sequentialWorkflowRunner(SequentialWorkflowRunner.withBexEngine(
                                BexEngine.builder().build(),
                                100_000L))
                        .build());
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: BEX Only",
                "          - $return: {}"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("BEX Only", onlyEvent(result).get("/kind"));
    }

    @Test
    void literalTriggerAndUpdateDocumentStepsStillWork() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Apply",
                "        type: Coordination/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path: /status",
                "            val: 42",
                "      - name: Trigger",
                "        type: Coordination/Trigger Event",
                "        event:",
                "          type: Coordination/Event",
                "          kind: Existing Trigger",
                "          status: static"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals(BigInteger.valueOf(42), result.document().get("/status"));
        assertEquals("Existing Trigger", onlyEvent(result).get("/kind"));
        assertEquals("static", onlyEvent(result).get("/status"));
    }

    @Test
    void bexEngineCompileCacheIsUsedAcrossRuns() {
        final List<BexMetrics> metrics = new ArrayList<BexMetrics>();
        BexEngine engine = BexEngine.builder().metrics(new BexMetricsSink() {
            @Override
            public void accept(BexMetrics item) {
                metrics.add(item.copy());
            }
        }).build();
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                CoordinationProcessorOptions.builder().bexEngine(engine).build());
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        expr:",
                "          $document: /status"));

        Node afterFirst = support.processRun(document).document();
        support.processRun(afterFirst);

        long hits = 0L;
        for (BexMetrics item : metrics) {
            hits += item.compileCacheHits();
        }
        assertTrue(hits > 0L);
    }

    @Test
    void runnerProvidesFrozenStepAndContractNodesToExecutors() {
        final AtomicBoolean sawFrozenStep = new AtomicBoolean(false);
        final AtomicBoolean sawFrozenContract = new AtomicBoolean(false);
        WorkflowStepExecutor<Compute> executor = new WorkflowStepExecutor<Compute>() {
            @Override
            public boolean supports(SequentialWorkflowStep step) {
                return step instanceof Compute;
            }

            @Override
            public WorkflowStepResult execute(Compute step, StepExecutionContext context) {
                sawFrozenStep.set(context.stepFrozenNode() != null);
                sawFrozenContract.set(context.currentContractFrozenNode() != null);
                return WorkflowStepResult.none();
            }
        };
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                CoordinationProcessorOptions.builder()
                        .sequentialWorkflowRunner(new SequentialWorkflowRunner(
                                Collections.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>singletonList(executor)))
                        .build());
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    largePayload:",
                "      item000: value000",
                "      item001: value001",
                "      item002: value002",
                "    steps:",
                "      - name: Build",
                "        type: Coordination/Compute",
                "        do:",
                "          - $return: {}"));

        support.processRun(document);

        assertTrue(sawFrozenStep.get());
        assertTrue(sawFrozenContract.get());
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
}
