package blue.contract.processor.conversation.compute;

import blue.contract.processor.BlueDocumentProcessors;
import blue.contract.processor.BlueDocumentProcessorOptions;
import blue.contract.processor.conversation.bex.BexProcessingMetrics;
import blue.contract.processor.conversation.TestTimelineProvider;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.repo.BlueRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PaynoteReducedDefinitionWorkflowTest {
    private static final String DOCUMENT_RESOURCE = "/processor-delay/paynote-resale-reduced-bex.yaml";
    private static Fixture fixture;
    private static BexProcessingMetrics metrics;
    private static Node initializedDocument;
    private static Node hotelEvent;
    private static Node restaurantEvent;
    private static double setupBlueMs;
    private static double loadYamlMs;
    private static double initializeMs;
    private static double buildHotelEventMs;
    private static double buildRestaurantEventMs;

    @BeforeAll
    static void prepareFixture() throws IOException {
        long start = System.nanoTime();
        metrics = new BexProcessingMetrics();
        fixture = configuredFixture(metrics);
        setupBlueMs = elapsedMs(start);

        start = System.nanoTime();
        Node document = loadYaml(fixture, DOCUMENT_RESOURCE);
        loadYamlMs = elapsedMs(start);

        start = System.nanoTime();
        initializedDocument = fixture.blue.initializeDocument(document).document();
        initializeMs = elapsedMs(start);

        start = System.nanoTime();
        hotelEvent = participantOperation(fixture,
                "hotel-participant",
                1700000100,
                "hotelResaleOrderPlaced",
                subscriptionUpdate("hotel-resale-agreement",
                        "hotel-agreement-session",
                        "hotel-request-a",
                        "hotel-order-session-a"));
        buildHotelEventMs = elapsedMs(start);

        start = System.nanoTime();
        restaurantEvent = participantOperation(fixture,
                "restaurant-participant",
                1700000200,
                "restaurantResaleOrderPlaced",
                subscriptionUpdate("restaurant-resale-agreement",
                        "restaurant-agreement-session",
                        "restaurant-request-a",
                        "restaurant-order-session-a"));
        buildRestaurantEventMs = elapsedMs(start);
    }

    @Test
    void twoParticipantsCallDifferentOperationsBackedBySharedComputeDefinition() throws IOException {
        long totalStart = System.nanoTime();
        printSetupTimings();

        long start = System.nanoTime();
        DocumentProcessingResult hotelResult = fixture.blue.processDocument(initializedDocument, hotelEvent);
        printTiming("process hotel participant operation", start);

        assertFalse(hotelResult.capabilityFailure(), hotelResult.failureReason());
        assertEquals("placed", hotelResult.document().getAsText("/resaleOrderRequests/hotel-request-a/status"));
        assertEquals("hotel-order-session-a",
                hotelResult.document().getAsText("/resaleOrderRequests/hotel-request-a/orderSessionId"));
        assertEquals(Boolean.TRUE, hotelResult.document().get("/orders/package-order-a/hotelOrder/resalePlaced"));
        assertEquals("hotel-order-session-a",
                hotelResult.document().getAsText("/orders/package-order-a/hotelOrder/sessionId"));
        assertEquals("snapshot:component:hotel:hotel-order-session-a",
                hotelResult.document().getAsText("/orders/package-order-a/hotelOrder/snapshotRequestId"));
        assertEquals("agreement-linked:hotel:hotel-order-session-a",
                hotelResult.document().getAsText("/orders/package-order-a/hotelOrder/subscriptionId"));
        assertEquals("package-order-a",
                hotelResult.document().getAsText("/componentOrderRefsBySessionId/hotel-order-session-a/packageOrderSessionId"));
        assertEquals("hotelOrder",
                hotelResult.document().getAsText("/componentOrderRefsBySessionId/hotel-order-session-a/component"));
        assertContainsType(hotelResult.triggeredEvents(), "MyOS/Document Initial Snapshot Requested");
        assertContainsType(hotelResult.triggeredEvents(), "MyOS/Subscribe to Session Requested");

        start = System.nanoTime();
        DocumentProcessingResult restaurantResult = fixture.blue.processDocument(hotelResult.document(), restaurantEvent);
        printTiming("process restaurant participant operation", start);

        assertFalse(restaurantResult.capabilityFailure(), restaurantResult.failureReason());
        assertNotNull(restaurantResult.document());
        assertEquals("placed", restaurantResult.document().getAsText("/resaleOrderRequests/restaurant-request-a/status"));
        assertEquals("restaurant-order-session-a",
                restaurantResult.document().getAsText("/resaleOrderRequests/restaurant-request-a/orderSessionId"));
        assertEquals(Boolean.TRUE, restaurantResult.document().get("/orders/package-order-a/restaurantOrder/resalePlaced"));
        assertEquals("restaurant-order-session-a",
                restaurantResult.document().getAsText("/orders/package-order-a/restaurantOrder/sessionId"));
        assertEquals("snapshot:component:restaurant:restaurant-order-session-a",
                restaurantResult.document().getAsText("/orders/package-order-a/restaurantOrder/snapshotRequestId"));
        assertEquals("agreement-linked:restaurant:restaurant-order-session-a",
                restaurantResult.document().getAsText("/orders/package-order-a/restaurantOrder/subscriptionId"));
        assertEquals("package-order-a",
                restaurantResult.document().getAsText("/componentOrderRefsBySessionId/restaurant-order-session-a/packageOrderSessionId"));
        assertEquals("restaurantOrder",
                restaurantResult.document().getAsText("/componentOrderRefsBySessionId/restaurant-order-session-a/component"));
        assertEquals(Boolean.TRUE, restaurantResult.document().get("/orders/package-order-a/hotelOrder/resalePlaced"));
        assertContainsType(restaurantResult.triggeredEvents(), "MyOS/Document Initial Snapshot Requested");
        assertContainsType(restaurantResult.triggeredEvents(), "MyOS/Subscribe to Session Requested");
        printTiming("total reduced paynote flow", totalStart);
        printMetrics("reduced paynote flow metrics", metrics.snapshot());
    }

    @Test
    void eventProcessingOnlyTimingAfterWarmup() {
        DocumentProcessingResult warmHotel = fixture.blue.processDocument(initializedDocument, hotelEvent);
        assertFalse(warmHotel.capabilityFailure(), warmHotel.failureReason());
        DocumentProcessingResult warmRestaurant = fixture.blue.processDocument(warmHotel.document(), restaurantEvent);
        assertFalse(warmRestaurant.capabilityFailure(), warmRestaurant.failureReason());

        BexProcessingMetrics.Snapshot before = metrics.snapshot();
        long start = System.nanoTime();
        DocumentProcessingResult hotelResult = fixture.blue.processDocument(initializedDocument, hotelEvent);
        double processHotelMs = elapsedMs(start);

        start = System.nanoTime();
        DocumentProcessingResult restaurantResult = fixture.blue.processDocument(hotelResult.document(), restaurantEvent);
        double processRestaurantMs = elapsedMs(start);
        BexProcessingMetrics.Snapshot after = metrics.snapshot();

        assertFalse(hotelResult.capabilityFailure(), hotelResult.failureReason());
        assertFalse(restaurantResult.capabilityFailure(), restaurantResult.failureReason());
        assertEquals(Boolean.TRUE, restaurantResult.document().get("/orders/package-order-a/hotelOrder/resalePlaced"));
        assertEquals(Boolean.TRUE, restaurantResult.document().get("/orders/package-order-a/restaurantOrder/resalePlaced"));

        System.out.printf(Locale.ROOT,
                "Paynote reduced BEX event-only timing - processHotelMs: %.3fms, processRestaurantMs: %.3fms%n",
                processHotelMs,
                processRestaurantMs);
        printMetricsDelta("event-only metrics delta", before, after);
    }

    private static Node participantOperation(Fixture fixture,
                                             String timelineId,
                                             int timestamp,
                                             String operation,
                                             Node request) {
        Node message = new Node()
                .type("Conversation/Operation Request")
                .properties("operation", new Node().value(operation))
                .properties("request", request);
        return TestTimelineProvider.timelineEntry(fixture.blue, fixture.repository, timelineId, timestamp, message);
    }

    private static Node subscriptionUpdate(String subscriptionId,
                                           String targetSessionId,
                                           String requestId,
                                           String orderSessionId) {
        return new Node()
                .type("MyOS/Subscription Update")
                .properties("subscriptionId", new Node().value(subscriptionId))
                .properties("targetSessionId", new Node().value(targetSessionId))
                .properties("update", new Node()
                        .properties("kind", new Node().value("Resale Order Placed"))
                        .properties("inResponseTo", new Node()
                                .properties("requestId", new Node().value(requestId)))
                        .properties("orderSessionId", new Node().value(orderSessionId)));
    }

    private static Node loadYaml(Fixture fixture, String resourcePath) throws IOException {
        Node node = fixture.blue.yamlToNode(readResource(resourcePath));
        node.blue(fixture.repository.typeAliasBlue());
        return fixture.blue.preprocess(node);
    }

    private static String readResource(String resourcePath) throws IOException {
        InputStream stream = PaynoteReducedDefinitionWorkflowTest.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IOException("Missing test resource: " + resourcePath);
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void assertContainsType(List<Node> events, String expectedType) {
        for (Node event : events) {
            if (expectedType.equals(typeName(event))) {
                return;
            }
        }
        throw new AssertionError("Expected emitted event type " + expectedType + " in " + events);
    }

    private static String typeName(Node event) {
        if (event == null) {
            return null;
        }
        if (event.getType() != null && event.getType().getValue() instanceof String) {
            return (String) event.getType().getValue();
        }
        Node type = event.getProperties() != null ? event.getProperties().get("type") : null;
        Object value = type != null ? type.getValue() : null;
        return value instanceof String ? (String) value : null;
    }

    private static void printTiming(String label, long startNanos) {
        System.out.printf(Locale.ROOT, "Paynote reduced BEX timing - %s: %.3fms%n",
                label,
                elapsedMs(startNanos));
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0d;
    }

    private static void printSetupTimings() {
        System.out.printf(Locale.ROOT, "Paynote reduced BEX setup timing - setupBlueMs: %.3fms%n", setupBlueMs);
        System.out.printf(Locale.ROOT, "Paynote reduced BEX setup timing - loadYamlMs: %.3fms%n", loadYamlMs);
        System.out.printf(Locale.ROOT, "Paynote reduced BEX setup timing - initializeMs: %.3fms%n", initializeMs);
        System.out.printf(Locale.ROOT, "Paynote reduced BEX setup timing - buildHotelEventMs: %.3fms%n", buildHotelEventMs);
        System.out.printf(Locale.ROOT, "Paynote reduced BEX setup timing - buildRestaurantEventMs: %.3fms%n", buildRestaurantEventMs);
    }

    private static void printMetrics(String label, BexProcessingMetrics.Snapshot snapshot) {
        printMetrics(label,
                snapshot.workflowStepsExecuted,
                snapshot.computeStepsExecuted,
                snapshot.updateDocumentStepsExecuted,
                snapshot.triggerEventStepsExecuted,
                snapshot.bexFieldEvaluations,
                snapshot.directBexChangesetHits,
                snapshot.genericBexChangesetEvaluations,
                snapshot.directBexEventHits,
                snapshot.genericBexEventEvaluations,
                snapshot.patchesApplied,
                snapshot.eventsEmitted,
                snapshot.computeProgramNormalizations,
                snapshot.computeDefinitionNormalizations);
    }

    private static void printMetrics(String label,
                                     long workflowStepsExecuted,
                                     long computeStepsExecuted,
                                     long updateDocumentStepsExecuted,
                                     long triggerEventStepsExecuted,
                                     long bexFieldEvaluations,
                                     long directBexChangesetHits,
                                     long genericBexChangesetEvaluations,
                                     long directBexEventHits,
                                     long genericBexEventEvaluations,
                                     long patchesApplied,
                                     long eventsEmitted,
                                     long computeProgramNormalizations,
                                     long computeDefinitionNormalizations) {
        System.out.printf(Locale.ROOT,
                "Paynote reduced BEX %s - workflowSteps=%d, computeSteps=%d, updateSteps=%d, triggerSteps=%d, " +
                        "bexFieldEvals=%d, directChangesetHits=%d, genericChangesetEvals=%d, " +
                        "directEventHits=%d, genericEventEvals=%d, patchesApplied=%d, eventsEmitted=%d, " +
                        "programNormalizations=%d, definitionNormalizations=%d%n",
                label,
                workflowStepsExecuted,
                computeStepsExecuted,
                updateDocumentStepsExecuted,
                triggerEventStepsExecuted,
                bexFieldEvaluations,
                directBexChangesetHits,
                genericBexChangesetEvaluations,
                directBexEventHits,
                genericBexEventEvaluations,
                patchesApplied,
                eventsEmitted,
                computeProgramNormalizations,
                computeDefinitionNormalizations);
    }

    private static void printMetricsDelta(String label,
                                          BexProcessingMetrics.Snapshot before,
                                          BexProcessingMetrics.Snapshot after) {
        printMetrics(label,
                after.workflowStepsExecuted - before.workflowStepsExecuted,
                after.computeStepsExecuted - before.computeStepsExecuted,
                after.updateDocumentStepsExecuted - before.updateDocumentStepsExecuted,
                after.triggerEventStepsExecuted - before.triggerEventStepsExecuted,
                after.bexFieldEvaluations - before.bexFieldEvaluations,
                after.directBexChangesetHits - before.directBexChangesetHits,
                after.genericBexChangesetEvaluations - before.genericBexChangesetEvaluations,
                after.directBexEventHits - before.directBexEventHits,
                after.genericBexEventEvaluations - before.genericBexEventEvaluations,
                after.patchesApplied - before.patchesApplied,
                after.eventsEmitted - before.eventsEmitted,
                after.computeProgramNormalizations - before.computeProgramNormalizations,
                after.computeDefinitionNormalizations - before.computeDefinitionNormalizations);
    }

    private static Fixture configuredFixture(BexProcessingMetrics metrics) {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        BlueDocumentProcessors.registerWith(blue, BlueDocumentProcessorOptions.builder()
                .processingMetrics(metrics)
                .build());
        TestTimelineProvider.registerWith(blue);
        return new Fixture(repository, blue);
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
