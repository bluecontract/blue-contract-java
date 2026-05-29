package blue.coordination.processor;

import blue.coordination.processor.CoordinationProcessors;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.HandlerProcessor;
import blue.language.processor.ProcessorExecutionContext;
import blue.language.processor.model.HandlerContract;
import blue.language.processor.model.JsonPatch;
import blue.language.snapshot.ResolvedSnapshot;
import blue.repo.BlueRepository;
import blue.repo.coordination.ChatMessage;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CounterSnapshotRoundTripStressTest {
    private static final int STRESS_ITERATIONS = 100;

    @Test
    void bexOnlyCounterUpdatesSurviveCanonicalSnapshotRoundTrips() {
        Fixture fixture = configuredFixture();
        DocumentProcessingResult initialized = fixture.blue.initializeDocument(
                fixture.blue.preprocess(bexOnlyCounterDocument(fixture.counterIncrementHandlerBlueId)
                        .blue(fixture.repository.typeAliasBlue())));
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
        assertEquals(minGas, maxGas, "equivalent BEX-only increments should charge stable gas");

        System.out.println("BEX-only counter snapshot round-trip stress: iterations=" + STRESS_ITERATIONS
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

    private static Node bexOnlyCounterDocument(String counterIncrementHandlerBlueId) {
        Map<String, Node> contracts = new LinkedHashMap<String, Node>();
        contracts.put("ownerChannel", TestTimelineProvider.channel("counter"));
        contracts.put("incrementImpl", new Node()
                .type(new Node().blueId(counterIncrementHandlerBlueId))
                .properties("channel", new Node().value("ownerChannel")));

        return new Node()
                .name("Counter")
                .properties("counter", new Node().value(0))
                .properties("contracts", new Node().properties(contracts));
    }

    private static void assertCounterMessage(Node event, int counter) {
        assertEquals("Counter is now " + counter, event.get("/message"));
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = CoordinationTestResources.configuredBlue(repository);
        CoordinationProcessors.registerWith(blue);
        TestTimelineProvider.registerWith(blue);
        Node counterIncrementHandlerType = new Node().name("Counter Increment Handler");
        String counterIncrementHandlerBlueId = blue.calculateBlueId(counterIncrementHandlerType);
        blue.registerExternalContractType(counterIncrementHandlerBlueId,
                counterIncrementHandlerType,
                new CounterIncrementHandlerProcessor());
        return new Fixture(repository, blue, counterIncrementHandlerBlueId);
    }

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
                    .type(new Node().blueId(ChatMessage.blueId()))
                    .properties("message", new Node().value("Counter is now " + next)));
        }
    }

    private static final class Fixture {
        private final BlueRepository repository;
        private final Blue blue;
        private final String counterIncrementHandlerBlueId;

        private Fixture(BlueRepository repository, Blue blue, String counterIncrementHandlerBlueId) {
            this.repository = repository;
            this.blue = blue;
            this.counterIncrementHandlerBlueId = counterIncrementHandlerBlueId;
        }
    }
}
