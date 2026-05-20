package blue.contract.processor.conversation.compute;

import blue.bex.api.BexEngine;
import blue.bex.api.BexMetricsSink;
import blue.bex.result.BexMetrics;
import blue.contract.processor.BlueDocumentProcessorOptions;
import blue.contract.processor.conversation.TestTimelineProvider;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.workflow.SequentialWorkflowRunner;
import blue.contract.processor.conversation.workflow.StepExecutionContext;
import blue.contract.processor.conversation.workflow.WorkflowStepExecutor;
import blue.contract.processor.conversation.workflow.WorkflowStepResult;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.ProcessorFatalException;
import blue.repo.conversation.Compute;
import blue.repo.conversation.SequentialWorkflowStep;

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

class ComputeWorkflowExecutionTest {
    @Test
    void inlineComputeEmitsEventAndDoesNotMutateDocument() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
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
                "        type: Conversation/Compute",
                "        do:",
                "          - $return:",
                "              approved: true",
                "              reason: ok",
                "      - name: ReadPrior",
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
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
                "        type: Conversation/Compute",
                "        emitEvents: false",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
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
                "        type: Conversation/Compute",
                "        emitEvents: false",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
                "              kind: Should Not Emit",
                "          - $return:",
                "              approved: true",
                "      - name: Read",
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
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
                "        type: Conversation/Compute",
                "        returnResult: false",
                "        do:",
                "          - $return:",
                "              approved: true",
                "      - name: ReadPrior",
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
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
                "        type: Conversation/Compute",
                "        returnResult: false",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
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
                "      - type: Conversation/Compute",
                "        do:",
                "          - $return:",
                "              value: abc",
                "      - name: Read",
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
                "              kind:",
                "                $steps: Step1.value",
                "          - $return: {}"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("abc", onlyEvent(result).get("/kind"));
    }

    @Test
    void computeChangesetIsDataAndDoesNotMutateDocument() {
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
                "      - name: VerifyPatchData",
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
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

        assertEquals("idle", result.document().get("/status"));
        assertEquals("/status", onlyEvent(result).get("/patchPath"));
        assertEquals("active", onlyEvent(result).get("/patchValue"));
    }

    @Test
    void expressionOnlyComputeExportsScalarResult() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: ReadStatus",
                "        type: Conversation/Compute",
                "        expr:",
                "          $document: /status",
                "      - name: EmitStatus",
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
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
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
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
                "  manualChannel:",
                "    type:",
                "      blueId: " + TestTimelineProvider.SIMPLE_TIMELINE_CHANNEL_BLUE_ID,
                "    timelineId: owner",
                "  run:",
                "    type: Conversation/Operation",
                "    channel: manualChannel",
                "  runImpl:",
                "    type: Conversation/Sequential Workflow Operation",
                "    operation: run",
                "    channel: manualChannel",
                "    steps:",
                "      - name: Build",
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
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
                "    type: Conversation/Compute Definition",
                "    constants:",
                "      kind: From Definition",
                "    functions:",
                "      build:",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
                "              kind:",
                "                $const: kind",
                "          - $return: {}"),
                String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Conversation/Compute",
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
                "    type: Conversation/Compute Definition",
                "    functions:",
                "      build:",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
                "              kind: Absolute Definition",
                "          - $return: {}"),
                String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Conversation/Compute",
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
                "        type: Conversation/Compute",
                "        definition:",
                "          constants:",
                "            kind: Inline Definition",
                "          functions:",
                "            build:",
                "              do:",
                "                - $appendEvent:",
                "                    type: Conversation/Event",
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
                "    type: Conversation/Compute Definition",
                "    functions:",
                "      build:",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
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
                "        type: Conversation/Compute",
                "        definition: missingCompute",
                "        entry: build"));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> support.processRun(document));

        assertTrue(ex.getMessage().contains("Compute definition not found"));
    }

    @Test
    void missingEntryFailsClosed() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initialize(support.yaml(support.operationWorkflowDocumentWithContracts(String.join("\n",
                "  computeLogic:",
                "    type: Conversation/Compute Definition",
                "    functions:",
                "      build:",
                "        do:",
                "          - $return: {}"),
                String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Conversation/Compute",
                "        definition: computeLogic",
                "        entry: missing")))).document();

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> support.processRun(document));

        assertTrue(ex.getMessage().contains("Unknown entry function"));
    }

    @Test
    void stepConstantsOverrideDefinitionConstants() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initialize(support.yaml(support.operationWorkflowDocumentWithContracts(String.join("\n",
                "  computeLogic:",
                "    type: Conversation/Compute Definition",
                "    constants:",
                "      kind: From Definition",
                "    functions:",
                "      build:",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
                "              kind:",
                "                $const: kind",
                "          - $return: {}"),
                String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Conversation/Compute",
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
                "    type: Conversation/Compute Definition",
                "    functions:",
                "      build:",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
                "              kind: Escaped Definition",
                "          - $return: {}"),
                String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Conversation/Compute",
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
                "        type: Conversation/Compute",
                "        entry: build",
                "        functions:",
                "          build:",
                "            do:",
                "              - $appendEvent:",
                "                  type: Conversation/Event",
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
                "        type: Conversation/Compute",
                "        gasLimit: 1",
                "        do:",
                "          - $return:",
                "              ok: true"));

        ProcessorFatalException explicit = assertThrows(ProcessorFatalException.class,
                () -> support.processRun(document));
        assertTrue(explicit.getMessage().toLowerCase().contains("gas"));

        ComputeWorkflowTestSupport lowDefault = ComputeWorkflowTestSupport.create(
                BlueDocumentProcessorOptions.builder().defaultComputeGasLimit(1L).build());
        Node lowDefaultDocument = lowDefault.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Conversation/Compute",
                "        do:",
                "          - $return:",
                "              ok: true"));
        ProcessorFatalException defaultFailure = assertThrows(ProcessorFatalException.class,
                () -> lowDefault.processRun(lowDefaultDocument));
        assertTrue(defaultFailure.getMessage().toLowerCase().contains("gas"));

        ComputeWorkflowTestSupport normalDefault = ComputeWorkflowTestSupport.create(
                BlueDocumentProcessorOptions.builder().defaultComputeGasLimit(100_000L).build());
        Node normalDocument = normalDefault.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Conversation/Compute",
                "        do:",
                "          - $return:",
                "              ok: true"));

        assertFalse(normalDefault.processRun(normalDocument).capabilityFailure());
    }

    @Test
    void defaultComputeGasLimitMustBePositive() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                () -> BlueDocumentProcessorOptions.builder().defaultComputeGasLimit(0L));
        assertTrue(zero.getMessage().contains("defaultComputeGasLimit must be positive"));

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> BlueDocumentProcessorOptions.builder().defaultComputeGasLimit(-1L));
        assertTrue(negative.getMessage().contains("defaultComputeGasLimit must be positive"));
    }

    @Test
    void explicitResultEventsAndAccumulatorEventsAreEmitted() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Explicit",
                "        type: Conversation/Compute",
                "        do:",
                "          - $return:",
                "              events:",
                "                - type: Conversation/Event",
                "                  kind: Explicit Events",
                "              changeset: []",
                "      - name: Accumulator",
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
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
                "        type: Conversation/Compute",
                "        do:",
                "          - $return:",
                "              events: not-a-list"));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> support.processRun(document));

        assertTrue(ex.getMessage().contains("Compute result events must be a list"));
    }

    @Test
    void scalarEventEntriesFailClosed() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Conversation/Compute",
                "        do:",
                "          - $return:",
                "              events:",
                "                - hello"));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> support.processRun(document));

        assertTrue(ex.getMessage().contains("Compute result events must contain object entries"));
    }

    @Test
    void nullEventEntriesFailClosed() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Conversation/Compute",
                "        do:",
                "          - $return:",
                "              events:",
                "                - null"));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> support.processRun(document));

        assertTrue(ex.getMessage().contains("Compute result events cannot contain undefined/null entries"));
    }

    @Test
    void pureComputeWorkflowDoesNotCallJavaScriptRuntime() {
        JavaScriptRuntime failingRuntime = new JavaScriptRuntime() {
            @Override
            public JavaScriptEvaluationResult evaluate(JavaScriptEvaluationRequest request) {
                throw new AssertionError("QuickJS must not be called");
            }
        };
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create(
                BlueDocumentProcessorOptions.builder()
                        .sequentialWorkflowRunner(SequentialWorkflowRunner.withRuntimes(
                                failingRuntime,
                                BexEngine.builder().build(),
                                100_000L))
                        .build());
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Conversation/Compute",
                "        do:",
                "          - $appendEvent:",
                "              type: Conversation/Event",
                "              kind: No QuickJS",
                "          - $return: {}"));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals("No QuickJS", onlyEvent(result).get("/kind"));
    }

    @Test
    void existingJavaScriptTriggerAndUpdateDocumentStepsStillWork() {
        ComputeWorkflowTestSupport support = ComputeWorkflowTestSupport.create();
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: ComputeValue",
                "        type: Conversation/JavaScript Code",
                "        code: \"return { value: 41 };\"",
                "      - name: Apply",
                "        type: Conversation/Update Document",
                "        changeset:",
                "          - op: replace",
                "            path: /status",
                "            val: \"${steps.ComputeValue.value + 1}\"",
                "      - name: Trigger",
                "        type: Conversation/Trigger Event",
                "        event:",
                "          type: Conversation/Event",
                "          kind: Existing Trigger",
                "          status: \"${document('/status')}\""));

        DocumentProcessingResult result = support.processRun(document);

        assertEquals(BigInteger.valueOf(42), result.document().get("/status"));
        assertEquals("Existing Trigger", onlyEvent(result).get("/kind"));
        assertEquals(BigInteger.valueOf(42), onlyEvent(result).get("/status"));
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
                BlueDocumentProcessorOptions.builder().bexEngine(engine).build());
        Node document = support.initializedOperationWorkflow(String.join("\n",
                "    steps:",
                "      - name: Build",
                "        type: Conversation/Compute",
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
                BlueDocumentProcessorOptions.builder()
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
                "        type: Conversation/Compute",
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
}
