package blue.coordination.processor;

import blue.coordination.processor.CoordinationProcessorOptions;
import blue.coordination.processor.CoordinationProcessors;
import blue.coordination.processor.workflow.SequentialWorkflowRunner;
import blue.coordination.processor.workflow.StepExecutionContext;
import blue.coordination.processor.workflow.UpdateDocumentStepExecutor;
import blue.coordination.processor.workflow.WorkflowStepExecutor;
import blue.coordination.processor.workflow.WorkflowStepResult;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.ProcessorStatus;
import blue.repo.BlueRepository;
import blue.repo.coordination.ChatMessage;
import blue.repo.coordination.SequentialWorkflowStep;
import blue.repo.coordination.TriggerEvent;
import blue.repo.coordination.UpdateDocument;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void decrementComputeWorks() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, counterDocument(fixture.repository, 10, true));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "decrement", 3);

        assertCounter(processed, 7);
    }

    @Test
    void multipleComputeStepsSeePreviousStepState() {
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

        DocumentProcessingResult result = fixture.blue.processDocument(document, event);

        assertRuntimeFatal(result, "Unsupported sequential workflow step");
    }

    @Test
    void coordinationProcessorOptionsInjectsSequentialWorkflowRunner() {
        WorkflowStepExecutor<UpdateDocument> injectedExecutor = new WorkflowStepExecutor<UpdateDocument>() {
            @Override
            public boolean supports(SequentialWorkflowStep step) {
                return step instanceof UpdateDocument;
            }

            @Override
            public WorkflowStepResult execute(UpdateDocument step, StepExecutionContext context) {
                context.processorContext().throwFatal("injected runner");
                return WorkflowStepResult.none();
            }
        };
        SequentialWorkflowRunner runner = new SequentialWorkflowRunner(
                Arrays.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>asList(
                        injectedExecutor));
        CoordinationProcessorOptions options = CoordinationProcessorOptions.builder()
                .sequentialWorkflowRunner(runner)
                .build();
        Fixture fixture = configuredCoordinationFixture(options);
        Node document = initializedDocument(fixture, staticUpdateDocument(fixture.repository,
                0,
                new Node().value(1)));

        DocumentProcessingResult result = processOperationRequestResult(fixture,
                document,
                "owner",
                1,
                "increment",
                new Node().value(7));

        assertRuntimeFatal(result, "injected runner");
    }

    @Test
    void literalUpdateValuesPassThrough() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, staticUpdateDocument(fixture.repository,
                0,
                new Node().properties("nested", new Node().value(true))));

        Node processed = processOperationRequest(fixture, document, "owner", 1, "increment", 7);

        assertEquals(Boolean.TRUE, processed.get("/counter/nested"));
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
        WorkflowStepExecutor<TriggerEvent> second = new WorkflowStepExecutor<TriggerEvent>() {
            @Override
            public boolean supports(SequentialWorkflowStep step) {
                return step instanceof TriggerEvent;
            }

            @Override
            public WorkflowStepResult execute(TriggerEvent step, StepExecutionContext context) {
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
    void computeEventStepSeesUpdatedDocument() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                updateDocumentStep("replace", "/counter", new Node().value(5)),
                computeAppendChatMessageStep(bexConcat(new Node().value("counter is "), bexText(bexDocument("/counter"))))));

        DocumentProcessingResult result = processChat(fixture, document, "owner", 1, "run");

        assertCounter(result.document(), 5);
        assertTriggeredChatMessage(result, "counter is 5");
    }

    @Test
    void triggerEventStepEmitsEvent() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                triggerEventStep("Workflow finished")));

        DocumentProcessingResult result = processChat(fixture, document, "owner", 1, "run");

        assertTriggeredChatMessage(result, "Workflow finished");
    }

    @Test
    void fullCounterWorkflowEmitsChatMessageWithTriggerEvent() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, counterWorkflowDocument(fixture.repository,
                0,
                computeReplaceCounterStep(incrementValue()),
                computeAppendChatMessageStep(bexConcat(
                        new Node().value("Counter was incremented by "),
                        bexBinding("event", "/message/request"),
                        new Node().value(" and is now "),
                        bexText(bexDocument("/counter"))))));

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
    void updateDocumentDoesNotCreateStepResult() {
        final AtomicReference<Integer> seenResultCount = new AtomicReference<Integer>();
        WorkflowStepExecutor<TriggerEvent> inspectStep = new WorkflowStepExecutor<TriggerEvent>() {
            @Override
            public boolean supports(SequentialWorkflowStep step) {
                return step instanceof TriggerEvent;
            }

            @Override
            public WorkflowStepResult execute(TriggerEvent step, StepExecutionContext context) {
                seenResultCount.set(context.stepResults().size());
                return WorkflowStepResult.none();
            }
        };
        SequentialWorkflowRunner runner = new SequentialWorkflowRunner(
                Arrays.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>asList(
                        new UpdateDocumentStepExecutor(),
                        inspectStep));
        Fixture fixture = configuredFixture(null, runner);
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                updateDocumentStep("replace", "/counter", new Node().value(3)),
                triggerEventStep("ignored").name("Inspect")));

        Node processed = processChat(fixture, document, "owner", 1, "run").document();

        assertCounter(processed, 3);
        assertEquals(Integer.valueOf(0), seenResultCount.get());
    }

    @Test
    void nullStepResultIsPreserved() {
        final AtomicReference<Boolean> sawNullResult = new AtomicReference<Boolean>();
        final AtomicReference<Boolean> firstCall = new AtomicReference<Boolean>(Boolean.TRUE);
        WorkflowStepExecutor<TriggerEvent> executor = new WorkflowStepExecutor<TriggerEvent>() {
            @Override
            public boolean supports(SequentialWorkflowStep step) {
                return step instanceof TriggerEvent;
            }

            @Override
            public WorkflowStepResult execute(TriggerEvent step, StepExecutionContext context) {
                if (Boolean.TRUE.equals(firstCall.get())) {
                    firstCall.set(Boolean.FALSE);
                    return WorkflowStepResult.value(null);
                }
                sawNullResult.set(context.stepResults().containsKey("MaybeNull")
                        && context.stepResults().get("MaybeNull") == null);
                return WorkflowStepResult.none();
            }
        };
        SequentialWorkflowRunner runner = new SequentialWorkflowRunner(
                Arrays.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>asList(
                        executor));
        Fixture fixture = configuredFixture(null, runner);
        Node document = initializedDocument(fixture, directWorkflowStepsDocument(fixture.repository,
                0,
                triggerEventStep("ignored").name("MaybeNull"),
                triggerEventStep("inspect").name("Inspect")));

        Node processed = processChat(fixture, document, "owner", 1, "run").document();

        assertCounter(processed, 0);
        assertEquals(Boolean.TRUE, sawNullResult.get());
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
                computeReplaceCounterStep(incrementValue())));
        if (includeDecrement) {
            contracts.put("decrement", operation("ownerChannel"));
            contracts.put("decrementImpl", sequentialWorkflowOperation("decrement",
                    computeReplaceCounterStep(decrementValue())));
        }
        return document(repository, counter, contracts);
    }

    private static Node counterWorkflowDocument(BlueRepository repository, int counter, Node... steps) {
        Map<String, Node> contracts = baseOperationContracts();
        contracts.put("increment", operation("ownerChannel"));
        contracts.put("incrementImpl", sequentialWorkflowOperation("increment", steps));
        return document(repository, counter, contracts);
    }

    private static Node staticUpdateDocument(BlueRepository repository, int counter, Node value) {
        return staticUpdateDocument(repository, new Node().value(counter), value);
    }

    private static Node staticUpdateDocument(BlueRepository repository, Node counter, Node value) {
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
                computeReplaceCounterStep(incrementValue()),
                computeReplaceCounterStep(incrementValue())));
        return document(repository, 0, contracts);
    }

    private static Node directWorkflowDocument(BlueRepository repository) {
        Map<String, Node> contracts = baseOperationContracts();
        contracts.put("direct", new Node()
                .type("Coordination/Sequential Workflow")
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
                .type("Coordination/Sequential Workflow")
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
                .type("Coordination/Sequential Workflow")
                .properties("channel", new Node().value("ownerChannel"))
                .properties("steps", new Node().items(new Node()
                        .type("Coordination/Sequential Workflow Step"))));
        return document(repository, 0, contracts);
    }

    private static Node stepResultsDocument(BlueRepository repository) {
        Map<String, Node> contracts = baseOperationContracts();
        contracts.put("direct", new Node()
                .type("Coordination/Sequential Workflow")
                .properties("channel", new Node().value("ownerChannel"))
                .properties("steps", new Node().items(
                        updateDocumentStep("replace", "/counter", new Node().value(1)),
                        triggerEventStep("ignored"))));
        return document(repository, 0, contracts);
    }

    private static Node embeddedScopeDocument(BlueRepository repository) {
        Map<String, Node> childContracts = baseOperationContracts();
        childContracts.put("increment", operation("ownerChannel"));
        childContracts.put("incrementImpl", sequentialWorkflowOperation("increment",
                computeReplaceCounterStep(incrementValue())));

        Map<String, Node> rootContracts = new LinkedHashMap<>();
        rootContracts.put("embedded", new Node()
                .type("Process Embedded")
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
                .type("Coordination/Operation")
                .properties("channel", new Node().value(channel))
                .properties("request", new Node().type("Integer"));
    }

    private static Node sequentialWorkflowOperation(String operation, Node... steps) {
        return new Node()
                .type("Coordination/Sequential Workflow Operation")
                .properties("operation", new Node().value(operation))
                .properties("steps", new Node().items(steps));
    }

    private static Node updateDocumentStep(String op, String path, Node value) {
        return new Node()
                .type("Coordination/Update Document")
                .properties("changeset", new Node().items(new Node()
                        .properties("op", new Node().value(op))
                        .properties("path", new Node().value(path))
                        .properties("val", value)));
    }

    private static Node computeReplaceCounterStep(Node value) {
        return new Node()
                .type("Coordination/Compute")
                .properties("do", new Node().items(
                        new Node().properties("$appendChange", new Node()
                                .properties("op", new Node().value("replace"))
                                .properties("path", new Node().value("/counter"))
                                .properties("val", value)),
                        new Node().properties("$return", new Node().value(true))));
    }

    private static Node incrementValue() {
        return bexAdd(bexDocument("/counter"), bexBinding("event", "/message/request"));
    }

    private static Node decrementValue() {
        return bexSubtract(bexDocument("/counter"), bexBinding("event", "/message/request"));
    }

    private static Node triggerEventStep(String message) {
        return new Node()
                .type("Coordination/Trigger Event")
                .properties("event", new Node()
                        .type("Coordination/Chat Message")
                        .properties("message", new Node().value(message)));
    }

    private static Node computeAppendChatMessageStep(Node message) {
        return new Node()
                .type("Coordination/Compute")
                .properties("do", new Node().items(
                        new Node().properties("$appendEvent", new Node().properties("$merge", new Node().items(
                                new Node().properties("type", new Node().value("Coordination/Chat Message")),
                                new Node().properties("message", message)))),
                        new Node().properties("$return", new Node().value(true))));
    }

    private static Node bexAdd(Node... values) {
        return new Node().properties("$add", new Node().items(values));
    }

    private static Node bexSubtract(Node... values) {
        return new Node().properties("$subtract", new Node().items(values));
    }

    private static Node bexConcat(Node... values) {
        return new Node().properties("$concat", new Node().items(values));
    }

    private static Node bexText(Node value) {
        return new Node().properties("$text", value);
    }

    private static Node bexDocument(String path) {
        return new Node().properties("$document", new Node().value(path));
    }

    private static Node bexBinding(String name, String path) {
        return new Node().properties("$binding", new Node().value(name + path));
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
                .type("Coordination/Timeline Entry")
                .properties("timeline", new Node()
                        .properties("timelineId", new Node().value(timelineId)))
                .properties("timestamp", new Node().value(timestamp))
                .properties("message", new Node()
                        .type("Coordination/Operation Request")
                        .properties("operation", new Node().value(operation))
                        .properties("request", request));
        return fixture.blue.preprocess(event).blue(null);
    }

    private static Node chatTimelineEntry(Fixture fixture, String timelineId, int timestamp, String message) {
        Node event = new Node()
                .blue(fixture.repository.typeAliasBlue())
                .type("Coordination/Timeline Entry")
                .properties("timeline", new Node()
                        .properties("timelineId", new Node().value(timelineId)))
                .properties("timestamp", new Node().value(timestamp))
                .properties("message", new Node()
                        .type("Coordination/Chat Message")
                        .properties("message", new Node().value(message)));
        return fixture.blue.preprocess(event).blue(null);
    }

    private static Node initializedDocument(Fixture fixture, Node document) {
        DocumentProcessingResult result = fixture.blue.initializeDocument(fixture.blue.preprocess(document));
        return result.document();
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = CoordinationTestResources.configuredBlue(repository);
        CoordinationProcessors.registerWith(blue);
        TestTimelineProvider.registerWith(blue);
        return new Fixture(repository, blue);
    }

    private static Fixture configuredFixture(CoordinationProcessorOptions options) {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = CoordinationTestResources.configuredBlue(repository);
        CoordinationProcessors.registerWith(blue, options);
        TestTimelineProvider.registerWith(blue);
        return new Fixture(repository, blue);
    }

    private static Fixture configuredCoordinationFixture(CoordinationProcessorOptions options) {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = CoordinationTestResources.configuredBlue(repository);
        CoordinationProcessors.registerWith(blue, options);
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

    private static void assertCounter(Node document, int expected) {
        assertEquals(BigInteger.valueOf(expected), document.get("/counter"));
    }

    private static void assertRuntimeFatal(DocumentProcessingResult result, String expectedMessage) {
        assertEquals(ProcessorStatus.RUNTIME_FATAL, result.status(), result.failureReason());
        assertTrue(result.failureReason() != null && result.failureReason().contains(expectedMessage),
                result.failureReason());
    }

    private static void assertTriggeredChatMessage(DocumentProcessingResult result, String expectedMessage) {
        for (Node event : result.triggeredEvents()) {
            if (isChatMessage(event)
                    && expectedMessage.equals(event.get("/message"))) {
                return;
            }
        }
        throw new AssertionError("Expected triggered chat message: " + expectedMessage
                + " in " + result.triggeredEvents());
    }

    private static boolean isChatMessage(Node event) {
        if (event == null) {
            return false;
        }
        Node type = event.getType();
        if (type != null) {
            return ChatMessage.qualifiedName().equals(type.getValue())
                    || ChatMessage.blueId().equals(type.getBlueId());
        }
        if (event.getProperties() == null) {
            return false;
        }
        Node typeProperty = event.getProperties().get("type");
        Object value = typeProperty != null ? typeProperty.getValue() : null;
        return ChatMessage.qualifiedName().equals(value) || ChatMessage.typeName().equals(value);
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
