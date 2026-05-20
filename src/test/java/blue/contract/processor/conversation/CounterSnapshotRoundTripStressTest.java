package blue.contract.processor.conversation;

import blue.contract.processor.BlueDocumentProcessors;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.model.TypeBlueId;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.HandlerProcessor;
import blue.language.processor.ProcessorExecutionContext;
import blue.language.processor.model.HandlerContract;
import blue.language.processor.model.JsonPatch;
import blue.language.snapshot.ResolvedSnapshot;
import blue.repo.BlueRepository;
import blue.repo.conversation.ChatMessage;
import blue.repo.conversation.Operation;
import blue.repo.conversation.OperationRequest;
import blue.repo.conversation.SequentialWorkflowOperation;
import blue.repo.conversation.SequentialWorkflowStep;
import blue.repo.conversation.TriggerEvent;
import blue.repo.conversation.UpdateDocument;
import blue.repo.core.JsonPatchEntry;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CounterSnapshotRoundTripStressTest {
    private static final int STRESS_ITERATIONS = 100;
    private static final String COUNTER_INCREMENT_HANDLER_BLUE_ID = "test-counter-increment-handler";

    @Test
    void noJsCounterUpdatesSurviveCanonicalSnapshotRoundTrips() {
        Fixture fixture = configuredFixture();
        DocumentProcessingResult initialized = fixture.blue.initializeDocument(
                fixture.blue.preprocess(noJsCounterDocument()));
        ResolvedSnapshot currentSnapshot = initialized.snapshot();
        assertNotNull(currentSnapshot);

        long started = System.nanoTime();
        long totalGas = 0L;
        long maxGas = 0L;
        long minGas = Long.MAX_VALUE;
        String finalBlueId = null;

        for (int i = 1; i <= STRESS_ITERATIONS; i++) {
            Node event = TestTimelineProvider.timelineEntry(fixture.blue,
                    fixture.repository,
                    "counter",
                    i,
                    TestTimelineProvider.chatMessage("tick " + i));

            DocumentProcessingResult result = fixture.blue.processDocument(currentSnapshot, event);

            assertNotNull(result.snapshot(), "iteration " + i + " should return a snapshot");
            assertNotNull(result.blueId(), "iteration " + i + " should return a BlueId");
            assertTrue(result.totalGas() > 0, "iteration " + i + " should charge gas");
            assertEquals(1, result.triggeredEvents().size(), "iteration " + i + " should emit one event");
            assertEquals(BigInteger.valueOf(i), result.resolvedDocument().get("/counter"));
            assertCounterMessage(result.triggeredEvents().get(0), i);

            totalGas += result.totalGas();
            maxGas = Math.max(maxGas, result.totalGas());
            minGas = Math.min(minGas, result.totalGas());
            finalBlueId = result.blueId();

            String canonicalJson = fixture.blue.nodeToJson(result.canonicalDocument());
            Node parsedCanonical = fixture.blue.jsonToNode(canonicalJson);
            ResolvedSnapshot loadedSnapshot = fixture.blue.loadSnapshot(parsedCanonical);

            assertEquals(result.blueId(), loadedSnapshot.blueId(), "iteration " + i + " should preserve BlueId");
            assertSnapshotCacheReuse(result.snapshot(), loadedSnapshot);
            currentSnapshot = loadedSnapshot;
        }

        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        assertEquals(BigInteger.valueOf(STRESS_ITERATIONS), currentSnapshot.resolvedNodeAt("/counter").getValue());
        assertNotNull(finalBlueId);
        assertTrue(totalGas > 0);
        assertTrue(maxGas > 0);
        assertTrue(minGas > 0);
        assertEquals(minGas, maxGas, "equivalent no-JS increments should charge stable gas");

        System.out.println("No-JS counter snapshot round-trip stress: iterations=" + STRESS_ITERATIONS
                + ", totalGas=" + totalGas
                + ", minGas=" + minGas
                + ", maxGas=" + maxGas
                + ", finalBlueId=" + finalBlueId
                + ", elapsedMillis=" + elapsedMillis);
    }

    @Test
    void quickJsCounterWorkflowSurvivesCanonicalSnapshotRoundTrips() {
        Fixture fixture = configuredFixture();
        DocumentProcessingResult initialized = fixture.blue.initializeDocument(
                fixture.blue.preprocess(quickJsCounterDocument(fixture.repository)));
        ResolvedSnapshot currentSnapshot = initialized.snapshot();
        assertNotNull(currentSnapshot);

        long started = System.nanoTime();
        long totalGas = 0L;
        long maxGas = 0L;
        long minGas = Long.MAX_VALUE;
        String finalBlueId = null;

        for (int i = 1; i <= STRESS_ITERATIONS; i++) {
            Node event = TestTimelineProvider.timelineEntry(fixture.blue,
                    fixture.repository,
                    "counter",
                    i,
                    operationRequest("increment", 1));

            DocumentProcessingResult result = fixture.blue.processDocument(currentSnapshot, event);

            assertNotNull(result.snapshot(), "iteration " + i + " should return a snapshot");
            assertNotNull(result.blueId(), "iteration " + i + " should return a BlueId");
            assertTrue(result.totalGas() > 0, "iteration " + i + " should charge gas");
            assertEquals(1, result.triggeredEvents().size(), "iteration " + i + " should emit one event");
            assertEquals(BigInteger.valueOf(i), result.resolvedDocument().get("/counter"));
            assertCounterMessage(result.triggeredEvents().get(0), i);

            totalGas += result.totalGas();
            maxGas = Math.max(maxGas, result.totalGas());
            minGas = Math.min(minGas, result.totalGas());
            finalBlueId = result.blueId();

            String canonicalJson = fixture.blue.nodeToJson(result.canonicalDocument());
            Node parsedCanonical = fixture.blue.jsonToNode(canonicalJson);
            ResolvedSnapshot loadedSnapshot = fixture.blue.loadSnapshot(parsedCanonical);

            assertEquals(result.blueId(), loadedSnapshot.blueId(), "iteration " + i + " should preserve BlueId");
            assertSnapshotCacheReuse(result.snapshot(), loadedSnapshot);
            currentSnapshot = loadedSnapshot;
        }

        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        assertEquals(BigInteger.valueOf(STRESS_ITERATIONS), currentSnapshot.resolvedNodeAt("/counter").getValue());
        assertNotNull(finalBlueId);
        assertTrue(totalGas > 0);
        assertTrue(maxGas > 0);
        assertTrue(minGas > 0);
        assertEquals(minGas, maxGas, "equivalent QuickJS increments should charge stable gas");

        System.out.println("QuickJS counter snapshot round-trip stress: iterations=" + STRESS_ITERATIONS
                + ", totalGas=" + totalGas
                + ", minGas=" + minGas
                + ", maxGas=" + maxGas
                + ", finalBlueId=" + finalBlueId
                + ", elapsedMillis=" + elapsedMillis);
    }

    private static void assertSnapshotCacheReuse(ResolvedSnapshot expected, ResolvedSnapshot actual) {
        if (expected == actual) {
            assertSame(expected, actual);
        } else {
            assertSame(expected.frozenResolvedRoot(), actual.frozenResolvedRoot());
        }
    }

    private static Node noJsCounterDocument() {
        Map<String, Node> contracts = new LinkedHashMap<String, Node>();
        contracts.put("ownerChannel", TestTimelineProvider.channel("counter"));
        contracts.put("incrementImpl", new Node()
                .type(new Node().blueId(COUNTER_INCREMENT_HANDLER_BLUE_ID))
                .properties("channel", new Node().value("ownerChannel")));

        return new Node()
                .name("Counter")
                .properties("counter", new Node().value(0))
                .properties("contracts", new Node().properties(contracts));
    }

    private static Node quickJsCounterDocument(BlueRepository repository) {
        Map<String, Node> contracts = new LinkedHashMap<String, Node>();
        contracts.put("ownerChannel", TestTimelineProvider.channel("counter"));
        contracts.put("increment", operationNode(new Operation()
                .channel("ownerChannel")
                .request(new Node().type("Integer"))));
        SequentialWorkflowOperation workflow = new SequentialWorkflowOperation().operation("increment");
        workflow.steps(Arrays.asList(updateDocumentStep(), triggerEventStep()));
        contracts.put("incrementImpl", sequentialWorkflowOperationNode(workflow));

        return new Node()
                .blue(repository.typeAliasBlue())
                .name("QuickJS Counter")
                .properties("counter", new Node().value(0))
                .properties("contracts", new Node().properties(contracts));
    }

    private static UpdateDocument updateDocumentStep() {
        return new UpdateDocument()
                .changeset(Arrays.asList(new JsonPatchEntry()
                        .op("replace")
                        .path("/counter")
                        .val(new Node().value("${event.message.request + document('/counter')}"))));
    }

    private static TriggerEvent triggerEventStep() {
        return new TriggerEvent()
                .event(chatMessageNode(new ChatMessage().message("Counter is now ${document('/counter')}")));
    }

    private static Node operationRequest(String operation, int request) {
        OperationRequest operationRequest = new OperationRequest()
                .operation(operation)
                .request(new Node().value(request));
        return new Node()
                .type(OperationRequest.qualifiedName())
                .properties("operation", new Node().value(operationRequest.getOperation()))
                .properties("request", operationRequest.getRequest());
    }

    private static Node operationNode(Operation operation) {
        return new Node()
                .type(Operation.qualifiedName())
                .properties("channel", new Node().value(operation.getChannel()))
                .properties("request", operation.getRequest());
    }

    private static Node sequentialWorkflowOperationNode(SequentialWorkflowOperation workflow) {
        List<Node> steps = new ArrayList<Node>();
        for (SequentialWorkflowStep step : workflow.getSteps()) {
            if (step instanceof UpdateDocument) {
                steps.add(updateDocumentStepNode((UpdateDocument) step));
            } else if (step instanceof TriggerEvent) {
                steps.add(triggerEventStepNode((TriggerEvent) step));
            }
        }
        return new Node()
                .type(SequentialWorkflowOperation.qualifiedName())
                .properties("operation", new Node().value(workflow.getOperation()))
                .properties("steps", new Node().items(steps));
    }

    private static Node updateDocumentStepNode(UpdateDocument step) {
        List<Node> changeset = new ArrayList<Node>();
        for (JsonPatchEntry entry : step.getChangeset()) {
            changeset.add(new Node()
                    .properties("op", new Node().value(entry.getOp()))
                    .properties("path", new Node().value(entry.getPath()))
                    .properties("val", entry.getVal()));
        }
        return new Node()
                .type(UpdateDocument.qualifiedName())
                .properties("changeset", new Node().items(changeset));
    }

    private static Node triggerEventStepNode(TriggerEvent step) {
        return new Node()
                .type(TriggerEvent.qualifiedName())
                .properties("event", step.getEvent());
    }

    private static Node chatMessageNode(ChatMessage chatMessage) {
        return new Node()
                .type(ChatMessage.qualifiedName())
                .properties("message", new Node().value(chatMessage.getMessage()));
    }

    private static void assertCounterMessage(Node event, int counter) {
        assertEquals("Counter is now " + counter, event.get("/message"));
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        BlueDocumentProcessors.registerWith(blue);
        TestTimelineProvider.registerWith(blue);
        blue.registerContractProcessor(new CounterIncrementHandlerProcessor());
        return new Fixture(repository, blue);
    }

    @TypeBlueId(CounterSnapshotRoundTripStressTest.COUNTER_INCREMENT_HANDLER_BLUE_ID)
    public static final class CounterIncrementHandler extends HandlerContract {
    }

    public static final class CounterIncrementHandlerProcessor
            implements HandlerProcessor<CounterIncrementHandler> {

        @Override
        public Class<CounterIncrementHandler> contractType() {
            return CounterIncrementHandler.class;
        }

        @Override
        public void execute(CounterIncrementHandler contract, ProcessorExecutionContext context) {
            Node current = context.documentAt(context.resolvePointer("/counter"));
            int value = ((Number) current.getValue()).intValue();
            int next = value + 1;

            context.applyPatch(JsonPatch.replace(
                    context.resolvePointer("/counter"),
                    new Node().value(next)));

            context.emitEvent(new Node()
                    .type("Conversation/Chat Message")
                    .properties("message", new Node().value("Counter is now " + next)));
        }
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
