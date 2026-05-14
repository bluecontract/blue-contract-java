package blue.contract.processor.conversation;

import blue.contract.processor.BlueDocumentProcessorOptions;
import blue.contract.processor.BlueDocumentProcessors;
import blue.contract.processor.ConversationProcessors;
import blue.contract.processor.conversation.expression.ExpressionEvaluator;
import blue.contract.processor.conversation.expression.QuickJsExpressionEvaluator;
import blue.contract.processor.conversation.expression.QuickJsExpressionResolver;
import blue.contract.processor.conversation.expression.SimpleExpressionEvaluator;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.NodeQuickJsRuntime;
import blue.contract.processor.conversation.javascript.QuickJsGas;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.workflow.JavaScriptCodeStepExecutor;
import blue.contract.processor.conversation.workflow.SequentialWorkflowRunner;
import blue.contract.processor.conversation.workflow.StepExecutionContext;
import blue.contract.processor.conversation.workflow.UpdateDocumentStepExecutor;
import blue.contract.processor.conversation.workflow.WorkflowStepExecutor;
import blue.contract.processor.conversation.workflow.WorkflowStepResult;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.ProcessorFatalException;
import blue.repo.BlueRepository;
import blue.repo.v1_2_0.conversation.JavaScriptCode;
import blue.repo.v1_2_0.conversation.SequentialWorkflowStep;
import blue.repo.v1_2_0.conversation.UpdateDocument;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static blue.language.utils.Properties.INTEGER_TYPE_BLUE_ID;

class SequentialWorkflowExecutionTest {

    @Test
    void sequentialWorkflowOperationDerivesAndMatchesOperationRequest() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, counterDocument(fixture.repository, 0, true));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "increment", 7);

        assertCounter(processed, 7);
    }

    @Test
    void wrongOperationDoesNotRun() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, counterDocument(fixture.repository, 0, false));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "decrement", 7);

        assertCounter(processed, 0);
    }

    @Test
    void wrongRequestTypeDoesNotRun() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, counterDocument(fixture.repository, 0, true));

        Node event = operationRequestEvent(fixture, "owner", 1, "increment", new Node().value("text"));
        Node processed = fixture.blue.processDocument(document, event).document();

        assertCounter(processed, 0);
    }

    @Test
    void duplicateRequestDoesNotRunTwice() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, counterDocument(fixture.repository, 0, true));
        Node event = operationRequestEvent(fixture, "owner", 1, "increment", new Node().value(7));

        Node afterFirst = fixture.blue.processDocument(document, event).document();
        Node afterSecond = fixture.blue.processDocument(afterFirst, event).document();

        assertCounter(afterSecond, 7);
    }

    @Test
    void newerRequestRunsAfterPreviousRequest() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, counterDocument(fixture.repository, 0, true));
        Node afterFirst = processOperationRequest(fixture, document, "owner", 1, "increment", 7);

        Node afterSecond = processOperationRequest(fixture, afterFirst, "owner", 2, "increment", 5);

        assertCounter(afterSecond, 12);
    }

    @Test
    void decrementExpressionWorks() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, counterDocument(fixture.repository, 10, true));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "decrement", 3);

        assertCounter(processed, 7);
    }

    @Test
    void multipleUpdateStepsSeePreviousStepState() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, doubleIncrementDocument(fixture.repository));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "increment", 2);

        assertCounter(processed, 4);
    }

    @Test
    void directSequentialWorkflowExecutesUpdateDocument() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowDocument(fixture.repository));
        Node event = chatTimelineEntry(fixture, "owner", 1, "run");

        Node processed = fixture.blue.processDocument(document, event).document();

        assertCounter(processed, 5);
    }

    @Test
    void unsupportedStepFailsExplicitly() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, unsupportedStepDocument(fixture.repository));
        Node event = chatTimelineEntry(fixture, "owner", 1, "run");

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> fixture.blue.processDocument(document, event));

        assertTrue(ex.getMessage().contains("Unsupported sequential workflow step"));
    }

    @Test
    void expressionEvaluatorIsSwappable() {
        ExpressionEvaluator fixedEvaluator = new ExpressionEvaluator() {
            @Override
            public Node evaluate(Node value, StepExecutionContext context) {
                return new Node().value(42);
            }
        };
        SequentialWorkflowRunner runner = new SequentialWorkflowRunner(
                Arrays.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>asList(
                        new UpdateDocumentStepExecutor(fixedEvaluator)));
        Fixture fixture = configuredFixture(runner, null);
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                0,
                new Node().value("${event.message.request + document('/counter')}")));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "increment", 7);

        assertCounter(processed, 42);
    }

    @Test
    void blueDocumentProcessorOptionsInjectsJavaScriptRuntime() {
        BlueDocumentProcessorOptions options = BlueDocumentProcessorOptions.builder()
                .javaScriptRuntime(fixedRuntime(42))
                .build();
        Fixture fixture = configuredFixture(options);
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                0,
                new Node().value("${1 + 1}")));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "increment", 7);

        assertCounter(processed, 42);
    }

    @Test
    void conversationProcessorOptionsInjectsSequentialWorkflowRunner() {
        ExpressionEvaluator fixedEvaluator = new ExpressionEvaluator() {
            @Override
            public Node evaluate(Node value, StepExecutionContext context) {
                return new Node().value(42);
            }
        };
        SequentialWorkflowRunner runner = new SequentialWorkflowRunner(
                Arrays.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>asList(
                        new UpdateDocumentStepExecutor(fixedEvaluator)));
        BlueDocumentProcessorOptions options = BlueDocumentProcessorOptions.builder()
                .sequentialWorkflowRunner(runner)
                .build();
        Fixture fixture = configuredConversationFixture(options);
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                0,
                new Node().value("${event.message.request + document('/counter')}")));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "increment", 7);

        assertCounter(processed, 42);
    }

    @Test
    void nonExpressionValuesPassThrough() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                0,
                new Node().properties("nested", new Node().value(true))));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "increment", 7);

        assertEquals(Boolean.TRUE, processed.get("/counter/nested"));
    }

    @Test
    void simpleUnsupportedExpressionFails() {
        Fixture fixture = configuredFixture(simpleExpressionRunner(), null);
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                1,
                new Node().value("${event.message.request * document('/counter')}")));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> processOperationRequest(fixture, document, "owner", 1, "increment", 7));

        assertTrue(ex.getMessage().contains("Unsupported expression"));
    }

    @Test
    void simpleDecimalArithmeticFails() {
        Fixture fixture = configuredFixture(simpleExpressionRunner(), null);
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                new Node().value(new BigDecimal("1.5")),
                new Node().value("${event.message.request + document('/counter')}")));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> processOperationRequest(fixture, document, "owner", 1, "increment", 7));

        assertTrue(ex.getMessage().contains("not an integer"));
    }

    @Test
    void simpleMissingEventPathFails() {
        Fixture fixture = configuredFixture(simpleExpressionRunner(), null);
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                1,
                new Node().value("${event.message.missing + document('/counter')}")));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> processOperationRequest(fixture, document, "owner", 1, "increment", 7));

        assertTrue(ex.getMessage().contains("Event expression path not found"));
    }

    @Test
    void simpleMissingDocumentPathFails() {
        Fixture fixture = configuredFixture(simpleExpressionRunner(), null);
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                1,
                new Node().value("${event.message.request + document('/missing')}")));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> processOperationRequest(fixture, document, "owner", 1, "increment", 7));

        assertTrue(ex.getMessage().contains("resolved to nothing"));
    }

    @Test
    void stepResultsAreCollected() {
        final AtomicReference<Map<String, Object>> seenResults = new AtomicReference<Map<String, Object>>();
        WorkflowStepExecutor<UpdateDocument> first = new WorkflowStepExecutor<UpdateDocument>() {
            @Override
            public boolean supports(SequentialWorkflowStep step) {
                return step instanceof UpdateDocument;
            }

            @Override
            public WorkflowStepResult execute(UpdateDocument step, StepExecutionContext context) {
                return WorkflowStepResult.value("a");
            }
        };
        WorkflowStepExecutor<JavaScriptCode> second = new WorkflowStepExecutor<JavaScriptCode>() {
            @Override
            public boolean supports(SequentialWorkflowStep step) {
                return step instanceof JavaScriptCode;
            }

            @Override
            public WorkflowStepResult execute(JavaScriptCode step, StepExecutionContext context) {
                seenResults.set(context.stepResults());
                return WorkflowStepResult.value("b");
            }
        };
        SequentialWorkflowRunner runner = new SequentialWorkflowRunner(
                Arrays.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>asList(first, second));
        Fixture fixture = configuredFixture(null, runner);
        Node document = initializedDocument(fixture, stepResultsDocument(fixture.repository));
        Node event = chatTimelineEntry(fixture, "owner", 1, "run");

        fixture.blue.processDocument(document, event);

        assertEquals(1, seenResults.get().size());
        assertEquals("a", seenResults.get().get("Step1"));
    }

    @Test
    void patchPathResolvesAgainstEmbeddedScope() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, embeddedScopeDocument(fixture.repository));
        Node event = operationRequestEvent(fixture, "owner", 1, "increment", new Node().value(7));

        Node processed = fixture.blue.processDocument(document, event).document();

        assertEquals(BigInteger.valueOf(100), processed.get("/counter"));
        assertEquals(BigInteger.valueOf(7), processed.get("/child/counter"));
    }

    @Test
    void quickJsSupportsConditionalExpression() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                1,
                new Node().value("${document('/counter') > 0 ? 'positive' : 'zero'}")));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "increment", 7);

        assertEquals("positive", processed.get("/counter"));
    }

    @Test
    void quickJsSupportsObjectResult() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                3,
                new Node().value("${({ previous: document('/counter'), request: event.message.request })}")));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "increment", 7);

        assertEquals(BigInteger.valueOf(3), processed.get("/counter/previous"));
        assertEquals(BigInteger.valueOf(7), processed.get("/counter/request"));
    }

    @Test
    void eventCanonicalBindingWorks() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                2,
                new Node().value("${eventCanonical.message.request.value + document('/counter')}")));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "increment", 7);

        assertCounter(processed, 9);
    }

    @Test
    void documentCanonicalBindingWorks() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                6,
                new Node().value("${document.canonical('/counter')}")));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "increment", 7);

        Node counter = processed.getProperties().get("counter");
        assertNotNull(counter);
        assertEquals(BigInteger.valueOf(6), counter.getValue());
        assertEquals(INTEGER_TYPE_BLUE_ID, counter.getType().getBlueId());
    }

    @Test
    void quickJsStepsBindingWorks() {
        WorkflowStepExecutor<JavaScriptCode> fakeFirstStep = new WorkflowStepExecutor<JavaScriptCode>() {
            @Override
            public boolean supports(SequentialWorkflowStep step) {
                return step instanceof JavaScriptCode;
            }

            @Override
            public WorkflowStepResult execute(JavaScriptCode step, StepExecutionContext context) {
                return WorkflowStepResult.value(5);
            }
        };
        SequentialWorkflowRunner runner = new SequentialWorkflowRunner(
                Arrays.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>asList(
                        fakeFirstStep,
                        new UpdateDocumentStepExecutor(new QuickJsExpressionEvaluator())));
        Fixture fixture = configuredFixture(null, runner);
        Node document = initializedDocument(fixture, quickJsStepsDocument(fixture.repository));
        Node event = chatTimelineEntry(fixture, "owner", 1, "run");

        Node processed = fixture.blue.processDocument(document, event).document();

        assertCounter(processed, 6);
    }

    @Test
    void quickJsGasConversionIsDeterministic() {
        assertEquals(0L, QuickJsGas.toWasmFuel(0L));
        assertEquals(3400L, QuickJsGas.toWasmFuel(2L));
        assertEquals(0L, QuickJsGas.toHostGasUsed(0L));
        assertEquals(1L, QuickJsGas.toHostGasUsed(1L));
        assertEquals(1L, QuickJsGas.toHostGasUsed(1700L));
        assertEquals(2L, QuickJsGas.toHostGasUsed(1701L));
    }

    @Test
    void quickJsGasIsCharged() {
        Fixture quickJsFixture = configuredFixture();
        Node quickJsDocument = initializedDocument(quickJsFixture,
                counterDocument(quickJsFixture.repository, 0, false));
        DocumentProcessingResult quickJsResult = quickJsFixture.blue.processDocument(
                quickJsDocument,
                operationRequestEvent(quickJsFixture, "owner", 1, "increment", new Node().value(7)));

        Fixture simpleFixture = configuredFixture(simpleExpressionRunner(), null);
        Node simpleDocument = initializedDocument(simpleFixture,
                counterDocument(simpleFixture.repository, 0, false));
        DocumentProcessingResult simpleResult = simpleFixture.blue.processDocument(
                simpleDocument,
                operationRequestEvent(simpleFixture, "owner", 1, "increment", new Node().value(7)));

        assertTrue(quickJsResult.totalGas() > simpleResult.totalGas());
    }

    @Test
    void quickJsRuntimeErrorFailsClearly() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                1,
                new Node().value("${missing.value}")));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> processOperationRequest(fixture, document, "owner", 1, "increment", 7));

        assertTrue(ex.getMessage().contains("QuickJS expression evaluation failed"));
        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void quickJsOutOfGasFailsClearly() {
        SequentialWorkflowRunner runner = new SequentialWorkflowRunner(
                Arrays.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>asList(
                        new UpdateDocumentStepExecutor(new QuickJsExpressionEvaluator(new NodeQuickJsRuntime(), 1L))));
        Fixture fixture = configuredFixture(runner, null);
        Node document = initializedDocument(fixture, expressionDocument(fixture.repository,
                1,
                new Node().value("${(() => { while (true) {} })()}")));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> processOperationRequest(fixture, document, "owner", 1, "increment", 7));

        assertTrue(ex.getMessage().contains("QuickJS expression evaluation failed"));
        assertTrue(ex.getMessage().toLowerCase().contains("gas"));
    }

    @Test
    void javaScriptCodeStepReturnIsVisibleToLaterUpdateExpression() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                javaScriptStep("Compute", "return { doubled: 21 * 2 };"),
                updateDocumentStep("replace", "/counter", new Node().value("${steps.Compute.doubled}"))));

        Node processed = processChat(fixture, document, "owner", 1, "run").document();

        assertCounter(processed, 42);
    }

    @Test
    void javaScriptCodeStepSeesUpdatedDocument() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                updateDocumentStep("replace", "/counter", new Node().value(5)),
                javaScriptStep("return { events: [{ type: \"Conversation/Chat Message\", message: `counter is ${document('/counter')}` }] };")));

        DocumentProcessingResult result = processChat(fixture, document, "owner", 1, "run");

        assertCounter(result.document(), 5);
        assertTriggeredChatMessage(result, "counter is 5");
    }

    @Test
    void javaScriptCodeStepEmitsEventsFromReturnedContainer() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                javaScriptStep("return { events: [{ type: \"Conversation/Chat Message\", message: \"Workflow finished\" }] };")));

        DocumentProcessingResult result = processChat(fixture, document, "owner", 1, "run");

        assertTriggeredChatMessage(result, "Workflow finished");
    }

    @Test
    void fullCounterJavaScriptCodeWorkflowEmitsChatMessage() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, counterWorkflowDocument(fixture.repository,
                0,
                updateDocumentStep("replace", "/counter",
                        new Node().value("${event.message.request + document('/counter')}")),
                javaScriptStep("const message =\n"
                        + "  `Counter was incremented by ${event.message.request} and is now ${document('/counter')}`;\n"
                        + "return { events: [{ type: \"Conversation/Chat Message\", message }] };")));

        DocumentProcessingResult result = processOperationRequestResult(fixture,
                document,
                "owner",
                1,
                "increment",
                new Node().value(7));

        assertCounter(result.document(), 7);
        assertTriggeredChatMessage(result, "Counter was incremented by 7 and is now 7");
    }

    @Test
    void javaScriptCodeEventAndCanonicalBindingsWork() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, counterWorkflowDocument(fixture.repository,
                0,
                javaScriptStep("return {\n"
                        + "  request: event.message.request,\n"
                        + "  canonicalRequest: eventCanonical.message.request.value\n"
                        + "};"),
                updateDocumentStep("replace", "/counter",
                        new Node().value("${steps.Step1.request + steps.Step1.canonicalRequest}"))));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "increment", 7);

        assertCounter(processed, 14);
    }

    @Test
    void documentCanonicalWorksInJavaScriptCodeBlocks() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                6,
                javaScriptStep("ReadCounter", "const canonical = document.canonical('/counter');\n"
                        + "return { plain: document('/counter'), canonicalValue: canonical.value };"),
                updateDocumentStep("replace", "/counter", new Node().value("${steps.ReadCounter}"))));

        Node processed = processChat(fixture, document, "owner", 1, "run").document();

        assertEquals(BigInteger.valueOf(6), processed.get("/counter/plain"));
        assertEquals(BigInteger.valueOf(6), processed.get("/counter/canonicalValue"));
    }

    @Test
    void previousJavaScriptStepResultsWork() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                javaScriptStep("First", "return { value: 34 };"),
                javaScriptStep("Second", "return steps.First.value + 8;"),
                updateDocumentStep("replace", "/counter", new Node().value("${steps.Second}"))));

        Node processed = processChat(fixture, document, "owner", 1, "run").document();

        assertCounter(processed, 42);
    }

    @Test
    void returnedJavaScriptObjectsDoNotMutateDocument() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                javaScriptStep("return { counter: 999 };")));

        Node processed = processChat(fixture, document, "owner", 1, "run").document();

        assertCounter(processed, 0);
    }

    @Test
    void blankJavaScriptCodeFailsClearly() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                javaScriptStep("   ")));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> processChat(fixture, document, "owner", 1, "run"));

        assertTrue(ex.getMessage().contains("JavaScript Code step must include code to execute"));
    }

    @Test
    void javaScriptCodeRuntimeErrorFailsClearly() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                javaScriptStep("throw new Error(\"boom\");")));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> processChat(fixture, document, "owner", 1, "run"));

        assertTrue(ex.getMessage().contains("JavaScript Code execution failed"));
        assertTrue(ex.getMessage().contains("boom"));
    }

    @Test
    void javaScriptCodeOutOfGasFailsClearly() {
        SequentialWorkflowRunner runner = new SequentialWorkflowRunner(
                Arrays.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>asList(
                        new JavaScriptCodeStepExecutor(new NodeQuickJsRuntime(), 1L)));
        Fixture fixture = configuredFixture(null, runner);
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                javaScriptStep("while (true) {}")));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> processChat(fixture, document, "owner", 1, "run"));

        assertTrue(ex.getMessage().contains("JavaScript Code execution failed"));
        assertTrue(ex.getMessage().toLowerCase().contains("gas"));
    }

    @Test
    void deterministicGlobalSurfaceIsUnavailableInJavaScriptCode() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                javaScriptStep("Globals", "return { date: typeof Date, process: typeof process };"),
                updateDocumentStep("replace", "/counter", new Node().value("${steps.Globals}"))));

        Node processed = processChat(fixture, document, "owner", 1, "run").document();

        assertEquals("undefined", processed.get("/counter/date"));
        assertEquals("undefined", processed.get("/counter/process"));
    }

    @Test
    void namedStepResultsWorkInUpdateDocument() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                javaScriptStep("Compute", "return { value: 12 };"),
                updateDocumentStep("replace", "/counter", new Node().value("${steps.Compute.value + 8}"))));

        Node processed = processChat(fixture, document, "owner", 1, "run").document();

        assertCounter(processed, 20);
    }

    @Test
    void updateDocumentDoesNotCreateStepResult() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                updateDocumentStep("replace", "/counter", new Node().value(3)),
                javaScriptStep("Inspect", "return Object.keys(steps).length;"),
                updateDocumentStep("replace", "/counter", new Node().value("${steps.Inspect}"))));

        Node processed = processChat(fixture, document, "owner", 1, "run").document();

        assertCounter(processed, 0);
    }

    @Test
    void javaScriptNullResultIsPreserved() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                javaScriptStep("MaybeNull", "return null;"),
                javaScriptStep("Check", "return Object.prototype.hasOwnProperty.call(steps, 'MaybeNull') && steps.MaybeNull === null;"),
                updateDocumentStep("replace", "/counter", new Node().value("${steps.Check}"))));

        Node processed = processChat(fixture, document, "owner", 1, "run").document();

        assertEquals(Boolean.TRUE, processed.get("/counter"));
    }

    @Test
    void currentContractBindingWorks() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                "Demo workflow",
                javaScriptStep("ReadContract", "return {\n"
                        + "  channel: currentContract.channel,\n"
                        + "  description: currentContract.description,\n"
                        + "  canonicalDescription: currentContractCanonical.description.value\n"
                        + "};"),
                updateDocumentStep("replace", "/counter", new Node().value("${steps.ReadContract}"))));

        Node processed = processChat(fixture, document, "owner", 1, "run").document();

        assertEquals("ownerChannel", processed.get("/counter/channel"));
        assertEquals("Demo workflow", processed.get("/counter/description"));
        assertEquals("Demo workflow", processed.get("/counter/canonicalDescription"));
    }

    @Test
    void derivedCurrentContractChannelWorks() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, counterWorkflowDocument(fixture.repository,
                0,
                javaScriptStep("ReadContract", "return currentContract.channel;"),
                updateDocumentStep("replace", "/counter", new Node().value("${steps.ReadContract}"))));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "increment", 7);

        assertEquals("ownerChannel", processed.get("/counter"));
    }

    @Test
    void documentRelativePointerWorks() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                4,
                javaScriptStep("Read", "return document('counter') + document('/counter') + (document().counter === 4 ? 1 : 0);"),
                updateDocumentStep("replace", "/counter", new Node().value("${steps.Read}"))));

        Node processed = processChat(fixture, document, "owner", 1, "run").document();

        assertCounter(processed, 9);
    }

    @Test
    void documentMissingPointerReturnsNull() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                javaScriptStep("Read", "return {\n"
                        + "  missing: document('/missing') === null,\n"
                        + "  missingCanonical: document.canonical('/missing') === null\n"
                        + "};"),
                updateDocumentStep("replace", "/counter", new Node().value("${steps.Read}"))));

        Node processed = processChat(fixture, document, "owner", 1, "run").document();

        assertEquals(Boolean.TRUE, processed.get("/counter/missing"));
        assertEquals(Boolean.TRUE, processed.get("/counter/missingCanonical"));
    }

    @Test
    void documentEscapedPointerWorks() {
        Fixture fixture = configuredFixture();
        Node document = directWorkflowStepsDocument(fixture.repository,
                0,
                javaScriptStep("Read", "return document('/a~1b') + document('/a~0b');"),
                updateDocumentStep("replace", "/counter", new Node().value("${steps.Read}")));
        document.properties("a/b", new Node().value(1));
        document.properties("a~b", new Node().value(2));
        Node initialized = initializedDocument(fixture, document);

        Node processed = processChat(fixture, initialized, "owner", 1, "run").document();

        assertCounter(processed, 3);
    }

    @Test
    void documentMetadataSegmentsWork() {
        Fixture fixture = configuredFixture();
        Node document = directWorkflowStepsDocument(fixture.repository,
                0,
                javaScriptStep("Read", "return {\n"
                        + "  name: document('/prop/name'),\n"
                        + "  description: document('/prop/description'),\n"
                        + "  valueOk: document('/prop/value') === 7\n"
                        + "};"),
                updateDocumentStep("replace", "/counter", new Node().value("${steps.Read}")));
        document.properties("prop", new Node()
                .name("Prop A")
                .description("Demo prop")
                .type("Integer")
                .value(7));
        Node initialized = initializedDocument(fixture, document);

        Node processed = processChat(fixture, initialized, "owner", 1, "run").document();

        assertEquals("Prop A", processed.get("/counter/name"));
        assertEquals("Demo prop", processed.get("/counter/description"));
        assertEquals(Boolean.TRUE, processed.get("/counter/valueOk"));
    }

    @Test
    void templateExpressionResolverResolvesTemplates() {
        QuickJsExpressionResolver resolver = new QuickJsExpressionResolver();

        Node resolved = resolver.resolve(new Node().value("Prepared ${steps.Prepare.amount} USD"), resolverBindings());

        assertEquals("Prepared 125 USD", resolved.getValue());
    }

    @Test
    void recursiveResolverIncludePredicateRestrictsEvaluation() {
        QuickJsExpressionResolver resolver = new QuickJsExpressionResolver();
        Node value = new Node()
                .properties("event", new Node()
                        .properties("message", new Node().value("Prepared ${steps.Prepare.amount} USD")))
                .properties("other", new Node().value("${steps.Prepare.amount}"));

        Node resolved = resolver.resolve(value, resolverBindings(), path -> path.equals("/event") || path.startsWith("/event/"), path -> true);

        assertEquals("Prepared 125 USD", resolved.get("/event/message"));
        assertEquals("${steps.Prepare.amount}", resolved.get("/other"));
    }

    @Test
    void recursiveResolverDescendPredicateCanKeepNestedDocumentsLiteral() {
        QuickJsExpressionResolver resolver = new QuickJsExpressionResolver();
        Node value = new Node()
                .properties("event", new Node()
                        .properties("message", new Node().value("Prepared ${steps.Prepare.amount} USD"))
                        .properties("embedded", new Node()
                                .properties("contracts", new Node()
                                        .properties("workflow", new Node()
                                                .properties("steps", new Node().items(new Node()
                                                        .properties("val", new Node().value("${steps.Prepare.amount}"))))))));

        Node resolved = resolver.resolve(value,
                resolverBindings(),
                path -> true,
                path -> !path.startsWith("/event/embedded"));

        assertEquals("Prepared 125 USD", resolved.get("/event/message"));
        assertEquals("${steps.Prepare.amount}", resolved.get("/event/embedded/contracts/workflow/steps/0/val"));
    }

    private static Node processOperationRequest(Fixture fixture,
                                                Node document,
                                                String timelineId,
                                                int timestamp,
                                                String operation,
                                                int request) {
        return processOperationRequestResult(fixture,
                document,
                timelineId,
                timestamp,
                operation,
                new Node().value(request)).document();
    }

    private static DocumentProcessingResult processOperationRequestResult(Fixture fixture,
                                                                          Node document,
                                                                          String timelineId,
                                                                          int timestamp,
                                                                          String operation,
                                                                          Node request) {
        Node event = operationRequestEvent(fixture, timelineId, timestamp, operation, request);
        return fixture.blue.processDocument(document, event);
    }

    private static DocumentProcessingResult processChat(Fixture fixture,
                                                        Node document,
                                                        String timelineId,
                                                        int timestamp,
                                                        String message) {
        Node event = chatTimelineEntry(fixture, timelineId, timestamp, message);
        return fixture.blue.processDocument(document, event);
    }

    private static Node counterDocument(BlueRepository repository, int counter, boolean includeDecrement) {
        Map<String, Node> contracts = baseOperationContracts();
        contracts.put("increment", operation("ownerChannel"));
        contracts.put("incrementImpl", sequentialWorkflowOperation("increment",
                updateDocumentStep("replace", "/counter",
                        new Node().value("${event.message.request + document('/counter')}"))));
        if (includeDecrement) {
            contracts.put("decrement", operation("ownerChannel"));
            contracts.put("decrementImpl", sequentialWorkflowOperation("decrement",
                    updateDocumentStep("replace", "/counter",
                            new Node().value("${document('/counter') - event.message.request}"))));
        }
        return document(repository, counter, contracts);
    }

    private static Node counterWorkflowDocument(BlueRepository repository, int counter, Node... steps) {
        Map<String, Node> contracts = baseOperationContracts();
        contracts.put("increment", operation("ownerChannel"));
        contracts.put("incrementImpl", sequentialWorkflowOperation("increment", steps));
        return document(repository, counter, contracts);
    }

    private static Node expressionDocument(BlueRepository repository, int counter, Node value) {
        return expressionDocument(repository, new Node().value(counter), value);
    }

    private static Node expressionDocument(BlueRepository repository, Node counter, Node value) {
        Map<String, Node> contracts = baseOperationContracts();
        contracts.put("increment", operation("ownerChannel"));
        contracts.put("incrementImpl", sequentialWorkflowOperation("increment",
                updateDocumentStep("replace", "/counter", value)));
        return document(repository, counter, contracts);
    }

    private static Node doubleIncrementDocument(BlueRepository repository) {
        Map<String, Node> contracts = baseOperationContracts();
        contracts.put("increment", operation("ownerChannel"));
        contracts.put("incrementImpl", sequentialWorkflowOperation("increment",
                updateDocumentStep("replace", "/counter",
                        new Node().value("${event.message.request + document('/counter')}")),
                updateDocumentStep("replace", "/counter",
                        new Node().value("${event.message.request + document('/counter')}"))));
        return document(repository, 0, contracts);
    }

    private static Node directWorkflowDocument(BlueRepository repository) {
        Map<String, Node> contracts = baseOperationContracts();
        contracts.put("direct", new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("ownerChannel"))
                .properties("event", new Node()
                        .properties("message", new Node()
                                .properties("message", new Node().value("run"))))
                .properties("steps", new Node().items(
                        updateDocumentStep("replace", "/counter", new Node().value(5)))));
        return document(repository, 0, contracts);
    }

    private static Node directWorkflowStepsDocument(BlueRepository repository, int counter, Node... steps) {
        return directWorkflowStepsDocument(repository, counter, null, steps);
    }

    private static Node directWorkflowStepsDocument(BlueRepository repository,
                                                    int counter,
                                                    String description,
                                                    Node... steps) {
        Map<String, Node> contracts = baseOperationContracts();
        Node workflow = new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("ownerChannel"))
                .properties("steps", new Node().items(steps));
        if (description != null) {
            workflow.description(description);
        }
        contracts.put("direct", workflow);
        return document(repository, counter, contracts);
    }

    private static Node unsupportedStepDocument(BlueRepository repository) {
        Map<String, Node> contracts = baseOperationContracts();
        contracts.put("direct", new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("ownerChannel"))
                .properties("steps", new Node().items(new Node()
                        .type("Conversation/Sequential Workflow Step"))));
        return document(repository, 0, contracts);
    }

    private static Node stepResultsDocument(BlueRepository repository) {
        Map<String, Node> contracts = baseOperationContracts();
        contracts.put("direct", new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("ownerChannel"))
                .properties("steps", new Node().items(
                        updateDocumentStep("replace", "/counter", new Node().value(1)),
                        new Node()
                                .type("Conversation/JavaScript Code")
                                .properties("code", new Node().value("return {};")))));
        return document(repository, 0, contracts);
    }

    private static Node quickJsStepsDocument(BlueRepository repository) {
        Map<String, Node> contracts = baseOperationContracts();
        contracts.put("direct", new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("ownerChannel"))
                .properties("steps", new Node().items(
                        new Node()
                                .type("Conversation/JavaScript Code")
                                .properties("code", new Node().value("return 5;")),
                        updateDocumentStep("replace", "/counter", new Node().value("${steps.Step1 + 1}")))));
        return document(repository, 0, contracts);
    }

    private static Node embeddedScopeDocument(BlueRepository repository) {
        Map<String, Node> childContracts = baseOperationContracts();
        childContracts.put("increment", operation("ownerChannel"));
        childContracts.put("incrementImpl", sequentialWorkflowOperation("increment",
                updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}"))));

        Map<String, Node> rootContracts = new LinkedHashMap<>();
        rootContracts.put("embedded", new Node()
                .type("Core/Process Embedded")
                .properties("paths", new Node().items(new Node().value("/child"))));

        return new Node()
                .blue(repository.typeAliasBlue())
                .name("Root")
                .properties("counter", new Node().value(100))
                .properties("child", new Node()
                        .name("Child")
                        .properties("counter", new Node().value(0))
                        .properties("contracts", new Node().properties(childContracts)))
                .properties("contracts", new Node().properties(rootContracts));
    }

    private static Map<String, Node> baseOperationContracts() {
        Map<String, Node> contracts = new LinkedHashMap<>();
        contracts.put("ownerChannel", TestTimelineProvider.channel("owner"));
        return contracts;
    }

    private static Node operation(String channel) {
        return new Node()
                .type("Conversation/Operation")
                .properties("channel", new Node().value(channel))
                .properties("request", new Node().type("Integer"));
    }

    private static Node sequentialWorkflowOperation(String operation, Node... steps) {
        return new Node()
                .type("Conversation/Sequential Workflow Operation")
                .properties("operation", new Node().value(operation))
                .properties("steps", new Node().items(steps));
    }

    private static Node updateDocumentStep(String op, String path, Node value) {
        return new Node()
                .type("Conversation/Update Document")
                .properties("changeset", new Node().items(new Node()
                        .properties("op", new Node().value(op))
                        .properties("path", new Node().value(path))
                        .properties("val", value)));
    }

    private static Node javaScriptStep(String code) {
        return new Node()
                .type("Conversation/JavaScript Code")
                .properties("code", new Node().value(code));
    }

    private static Node javaScriptStep(String name, String code) {
        return javaScriptStep(code).name(name);
    }

    private static Node document(BlueRepository repository, int counter, Map<String, Node> contracts) {
        return document(repository, new Node().value(counter), contracts);
    }

    private static Node document(BlueRepository repository, Node counter, Map<String, Node> contracts) {
        return new Node()
                .blue(repository.typeAliasBlue())
                .name("Counter")
                .properties("counter", counter)
                .properties("contracts", new Node().properties(contracts));
    }

    private static Node operationRequestEvent(Fixture fixture,
                                              String timelineId,
                                              int timestamp,
                                              String operation,
                                              Node request) {
        Node event = new Node()
                .blue(fixture.repository.typeAliasBlue())
                .type("Conversation/Timeline Entry")
                .properties("timeline", new Node()
                        .properties("timelineId", new Node().value(timelineId)))
                .properties("timestamp", new Node().value(timestamp))
                .properties("message", new Node()
                        .type("Conversation/Operation Request")
                        .properties("operation", new Node().value(operation))
                        .properties("request", request));
        return fixture.blue.preprocess(event);
    }

    private static Node chatTimelineEntry(Fixture fixture, String timelineId, int timestamp, String message) {
        Node event = new Node()
                .blue(fixture.repository.typeAliasBlue())
                .type("Conversation/Timeline Entry")
                .properties("timeline", new Node()
                        .properties("timelineId", new Node().value(timelineId)))
                .properties("timestamp", new Node().value(timestamp))
                .properties("message", new Node()
                        .type("Conversation/Chat Message")
                        .properties("message", new Node().value(message)));
        return fixture.blue.preprocess(event);
    }

    private static Node initializedDocument(Fixture fixture, Node document) {
        DocumentProcessingResult result = fixture.blue.initializeDocument(fixture.blue.preprocess(document));
        return result.document();
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_2_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        BlueDocumentProcessors.registerWith(blue);
        TestTimelineProvider.registerWith(blue);
        return new Fixture(repository, blue);
    }

    private static Fixture configuredFixture(BlueDocumentProcessorOptions options) {
        BlueRepository repository = BlueRepository.v1_2_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        BlueDocumentProcessors.registerWith(blue, options);
        TestTimelineProvider.registerWith(blue);
        return new Fixture(repository, blue);
    }

    private static Fixture configuredConversationFixture(BlueDocumentProcessorOptions options) {
        BlueRepository repository = BlueRepository.v1_2_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        ConversationProcessors.registerWith(blue, options);
        TestTimelineProvider.registerWith(blue);
        return new Fixture(repository, blue);
    }

    private static Fixture configuredFixture(SequentialWorkflowRunner operationRunner,
                                             SequentialWorkflowRunner directRunner) {
        Fixture fixture = configuredFixture();
        if (operationRunner != null) {
            fixture.blue.registerContractProcessor(new SequentialWorkflowOperationProcessor(operationRunner));
        }
        if (directRunner != null) {
            fixture.blue.registerContractProcessor(new SequentialWorkflowProcessor(directRunner));
        }
        return fixture;
    }

    private static SequentialWorkflowRunner simpleExpressionRunner() {
        return new SequentialWorkflowRunner(
                Arrays.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>asList(
                        new UpdateDocumentStepExecutor(new SimpleExpressionEvaluator())));
    }

    private static Map<String, Object> resolverBindings() {
        Map<String, Object> prepare = new LinkedHashMap<String, Object>();
        prepare.put("amount", 125);
        Map<String, Object> steps = new LinkedHashMap<String, Object>();
        steps.put("Prepare", prepare);
        Map<String, Object> bindings = new LinkedHashMap<String, Object>();
        bindings.put("steps", steps);
        bindings.put("event", null);
        bindings.put("eventCanonical", null);
        bindings.put("currentContract", null);
        bindings.put("currentContractCanonical", null);
        bindings.put("document", null);
        bindings.put("documentCanonical", null);
        return bindings;
    }

    private static JavaScriptRuntime fixedRuntime(final Object value) {
        return new JavaScriptRuntime() {
            @Override
            public JavaScriptEvaluationResult evaluate(JavaScriptEvaluationRequest request) {
                return new JavaScriptEvaluationResult(value, QuickJsGas.WASM_FUEL_PER_HOST_GAS_UNIT, 1L);
            }
        };
    }

    private static void assertCounter(Node document, int expected) {
        assertEquals(BigInteger.valueOf(expected), document.get("/counter"));
    }

    private static void assertTriggeredChatMessage(DocumentProcessingResult result, String expectedMessage) {
        assertEquals(1, result.triggeredEvents().size());
        Node event = result.triggeredEvents().get(0);
        assertEquals("Conversation/Chat Message", event.getType().getValue());
        assertEquals(expectedMessage, event.get("/message"));
    }

    private static final class Fixture {
        private final BlueRepository repository;
        private final Blue blue;

        private Fixture(BlueRepository repository, Blue blue) {
            this.repository = repository;
            this.blue = blue;
        }
    }
}
