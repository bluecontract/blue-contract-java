package blue.contract.processor.conversation;

import blue.contract.processor.BlueDocumentProcessors;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.model.Schema;
import blue.language.processor.DocumentProcessingResult;
import blue.repo.BlueRepository;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationRequestMatchingTest {

    @Test
    void directOperationRequestRunsThroughTriggeredChannel() {
        Fixture fixture = configuredFixture();
        Map<String, Node> contracts = ownerContracts();
        contracts.put("triggered", triggeredChannel());
        contracts.put("increment", operation("triggered", integerPattern()));
        contracts.put("incrementImpl", sequentialWorkflowOperation("increment",
                updateDocumentStep("replace", "/counter",
                        new Node().value("${event.request + document('/counter')}"))));
        contracts.put("producer", directWorkflow("owner",
                triggerEventStep(operationRequestEventNode("increment", new Node().value(7)))));
        Node initialized = initializedDocument(fixture, document(fixture.repository, 0, contracts));

        Node processed = processChat(fixture, initialized, "owner", 1).document();

        assertCounter(processed, 7);
    }

    @Test
    void timelineEntryOperationRequestStillRuns() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, timelineCounterDocument(fixture.repository,
                operation("owner", integerPattern()),
                sequentialWorkflowOperation("increment",
                        updateDocumentStep("replace", "/counter",
                                new Node().value("${event.message.request + document('/counter')}")))));

        Node processed = processOperationRequest(fixture, initialized, "owner", 1, "increment", new Node().value(7));

        assertCounter(processed, 7);
    }

    @Test
    void sequentialWorkflowOperationEventPatternAllowsMatchingEvent() {
        Fixture fixture = configuredFixture();
        Node workflow = sequentialWorkflowOperation("increment",
                updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}")));
        workflow.properties("event", new Node()
                .type("Conversation/Timeline Entry")
                .properties("source", new Node()
                        .properties("value", new Node().value("web"))));
        Node initialized = initializedDocument(fixture, timelineCounterDocument(fixture.repository,
                operation("owner", integerPattern()),
                workflow));

        Node processed = processOperationRequest(fixture, initialized, "owner", 1, "increment", new Node().value(7), "web");

        assertCounter(processed, 7);
    }

    @Test
    void sequentialWorkflowOperationEventPatternRejectsDifferentEvent() {
        Fixture fixture = configuredFixture();
        Node workflow = sequentialWorkflowOperation("increment",
                updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}")));
        workflow.properties("event", new Node()
                .type("Conversation/Timeline Entry")
                .properties("source", new Node()
                        .properties("value", new Node().value("web"))));
        Node initialized = initializedDocument(fixture, timelineCounterDocument(fixture.repository,
                operation("owner", integerPattern()),
                workflow));

        Node processed = processOperationRequest(fixture, initialized, "owner", 1, "increment", new Node().value(7), "api");

        assertCounter(processed, 0);
    }

    @Test
    void operationHandlerCanDeriveChannelFromOperation() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, timelineCounterDocument(fixture.repository,
                operation("owner", integerPattern()),
                sequentialWorkflowOperation("increment",
                        updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}")))));

        Node processed = processOperationRequest(fixture, initialized, "owner", 1, "increment", new Node().value(7));

        assertCounter(processed, 7);
    }

    @Test
    void operationHandlerCanDeclareSameChannelAsOperation() {
        Fixture fixture = configuredFixture();
        Node workflow = sequentialWorkflowOperation("increment",
                updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}")));
        workflow.properties("channel", new Node().value("owner"));
        Node initialized = initializedDocument(fixture, timelineCounterDocument(fixture.repository,
                operation("owner", integerPattern()),
                workflow));

        Node processed = processOperationRequest(fixture, initialized, "owner", 1, "increment", new Node().value(7));

        assertCounter(processed, 7);
    }

    @Test
    void operationWithoutChannelCanUseExplicitHandlerChannel() {
        Fixture fixture = configuredFixture();
        Node workflow = sequentialWorkflowOperation("increment",
                updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}")));
        workflow.properties("channel", new Node().value("owner"));
        Node initialized = initializedDocument(fixture, timelineCounterDocument(fixture.repository,
                operation(null, integerPattern()),
                workflow));

        Node processed = processOperationRequest(fixture, initialized, "owner", 1, "increment", new Node().value(7));

        assertCounter(processed, 7);
    }

    @Test
    void conflictingOperationAndHandlerChannelsDoNotRun() {
        Fixture fixture = configuredFixture();
        Map<String, Node> contracts = ownerContracts();
        contracts.put("other", timelineChannel("other"));
        contracts.put("increment", operation("owner", integerPattern()));
        Node workflow = sequentialWorkflowOperation("increment",
                updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}")));
        workflow.properties("channel", new Node().value("other"));
        contracts.put("incrementImpl", workflow);
        Node initialized = initializedDocument(fixture, document(fixture.repository, 0, contracts));

        Node processed = processOperationRequest(fixture, initialized, "other", 1, "increment", new Node().value(7));

        assertCounter(processed, 0);
    }

    @Test
    void integerRequestPatternAcceptsIntegerAndRejectsText() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, timelineCounterDocument(fixture.repository,
                operation("owner", integerPattern()),
                sequentialWorkflowOperation("increment",
                        updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}")))));

        Node afterInteger = processOperationRequest(fixture, initialized, "owner", 1, "increment", new Node().value(7));
        Node afterText = processOperationRequest(fixture, afterInteger, "owner", 2, "increment", new Node().value("7"));

        assertCounter(afterInteger, 7);
        assertCounter(afterText, 7);
    }

    @Test
    void objectRequestPatternAcceptsRequiredNestedProperty() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, timelineCounterDocument(fixture.repository,
                operation("owner", objectAmountPattern()),
                sequentialWorkflowOperation("increment",
                        updateDocumentStep("replace", "/counter",
                                new Node().value("${event.message.request.amount + document('/counter')}")))));

        Node processed = processOperationRequest(fixture, initialized, "owner", 1, "increment",
                new Node().properties("amount", new Node().value(7)));

        assertCounter(processed, 7);
    }

    @Test
    void objectRequestPatternRejectsMissingRequiredNestedProperty() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, timelineCounterDocument(fixture.repository,
                operation("owner", objectAmountPattern()),
                sequentialWorkflowOperation("increment",
                        updateDocumentStep("replace", "/counter",
                                new Node().value("${event.message.request.amount + document('/counter')}")))));

        Node processed = processOperationRequest(fixture, initialized, "owner", 1, "increment",
                new Node().properties("ignored", new Node().value(7)));

        assertCounter(processed, 0);
    }

    @Test
    void requestPatternIgnoresIrrelevantLargePayloadBranches() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, timelineCounterDocument(fixture.repository,
                operation("owner", objectAmountPattern()),
                sequentialWorkflowOperation("increment",
                        updateDocumentStep("replace", "/counter",
                                new Node().value("${event.message.request.amount + document('/counter')}")))));
        Node irrelevant = new Node().properties("nested", largePayloadBranch());
        Node request = new Node()
                .properties("amount", new Node().value(7))
                .properties("irrelevant", irrelevant);

        Node processed = processOperationRequest(fixture, initialized, "owner", 1, "increment", request);

        // Behavioral coverage: the shared FrozenTypeMatcher is path-local and
        // only needs the requested amount field for this pattern.
        assertCounter(processed, 7);
    }

    @Test
    void pinnedMatchingInitialDocumentRunsWhenNewerVersionIsNotAllowed() {
        Fixture fixture = configuredFixture();
        Node original = timelineCounterDocument(fixture.repository,
                operation("owner", integerPattern()),
                sequentialWorkflowOperation("increment",
                        updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}"))));
        Node initialized = initializedDocument(fixture, original);
        Node pinned = new Node().blueId((String) initialized.get("/contracts/initialized/documentId"));

        Node processed = processOperationRequest(fixture, initialized, "owner", 1,
                operationRequestEventNode("increment", new Node().value(7))
                        .properties("allowNewerVersion", new Node().value(false))
                        .properties("document", pinned.clone()));

        assertCounter(processed, 7);
    }

    @Test
    void pinnedStaleDocumentDoesNotRunWhenNewerVersionIsNotAllowed() {
        Fixture fixture = configuredFixture();
        Node original = timelineCounterDocument(fixture.repository,
                operation("owner", integerPattern()),
                sequentialWorkflowOperation("increment",
                        updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}"))));
        Node initialized = initializedDocument(fixture, original);
        Node stale = new Node().blueId("stale-document-blue-id");

        Node processed = processOperationRequest(fixture, initialized, "owner", 1,
                operationRequestEventNode("increment", new Node().value(7))
                        .properties("allowNewerVersion", new Node().value(false))
                        .properties("document", stale));

        assertCounter(processed, 0);
    }

    @Test
    void pinnedFullDocumentBodyIsComparedToInitializedDocumentIdWhenNewerVersionIsNotAllowed() {
        Fixture fixture = configuredFixture();
        Node matchingOriginal = timelineCounterDocument(fixture.repository,
                operation("owner", integerPattern()),
                sequentialWorkflowOperation("increment",
                        updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}"))));
        Node initialized = initializedDocument(fixture, matchingOriginal);
        Node matchingPinnedBody = fixture.blue.resolveToSnapshot(matchingOriginal).canonicalRoot();
        Node differentPinnedBody = fixture.blue.resolveToSnapshot(timelineCounterDocument(fixture.repository,
                99,
                operation("owner", integerPattern()),
                sequentialWorkflowOperation("increment",
                        updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}")))))
                .canonicalRoot();

        Node afterMatchingBody = processOperationRequest(fixture, initialized, "owner", 1,
                operationRequestEventNode("increment", new Node().value(7))
                        .properties("allowNewerVersion", new Node().value(false))
                        .properties("document", matchingPinnedBody));
        Node afterDifferentBody = processOperationRequest(fixture, initialized, "owner", 2,
                operationRequestEventNode("increment", new Node().value(7))
                        .properties("allowNewerVersion", new Node().value(false))
                        .properties("document", differentPinnedBody));

        assertCounter(afterMatchingBody, 7);
        assertCounter(afterDifferentBody, 0);
    }

    @Test
    void allowNewerVersionTrueRunsWithStalePinnedDocument() {
        Fixture fixture = configuredFixture();
        Node original = timelineCounterDocument(fixture.repository,
                operation("owner", integerPattern()),
                sequentialWorkflowOperation("increment",
                        updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}"))));
        Node initialized = initializedDocument(fixture, original);
        Node stale = new Node().blueId("stale-document-blue-id");

        Node processed = processOperationRequest(fixture, initialized, "owner", 1,
                operationRequestEventNode("increment", new Node().value(7))
                        .properties("allowNewerVersion", new Node().value(true))
                        .properties("document", stale));

        assertCounter(processed, 7);
    }

    @Test
    void missingPinnedDocumentRunsWhenNewerVersionIsNotAllowed() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, timelineCounterDocument(fixture.repository,
                operation("owner", integerPattern()),
                sequentialWorkflowOperation("increment",
                        updateDocumentStep("replace", "/counter", new Node().value("${event.message.request + document('/counter')}")))));

        Node processed = processOperationRequest(fixture, initialized, "owner", 1,
                operationRequestEventNode("increment", new Node().value(7))
                        .properties("allowNewerVersion", new Node().value(false)));

        assertCounter(processed, 7);
    }

    private static Node timelineCounterDocument(BlueRepository repository, Node operation, Node handler) {
        return timelineCounterDocument(repository, 0, operation, handler);
    }

    private static Node timelineCounterDocument(BlueRepository repository, int counter, Node operation, Node handler) {
        Map<String, Node> contracts = ownerContracts();
        contracts.put("increment", operation);
        contracts.put("incrementImpl", handler);
        return document(repository, counter, contracts);
    }

    private static Map<String, Node> ownerContracts() {
        Map<String, Node> contracts = new LinkedHashMap<String, Node>();
        contracts.put("owner", timelineChannel("owner"));
        return contracts;
    }

    private static Node timelineChannel(String timelineId) {
        return TestTimelineProvider.channel(timelineId);
    }

    private static Node triggeredChannel() {
        return new Node().type("Core/Triggered Event Channel");
    }

    private static Node operation(String channel, Node requestPattern) {
        Node operation = new Node()
                .type("Conversation/Operation")
                .properties("request", requestPattern);
        if (channel != null) {
            operation.properties("channel", new Node().value(channel));
        }
        return operation;
    }

    private static Node integerPattern() {
        return new Node().type("Integer");
    }

    private static Node objectAmountPattern() {
        return new Node().properties("amount", new Node()
                .type("Integer")
                .value(7)
                .schema(new Schema().required(new Node().value(true))));
    }

    private static Node sequentialWorkflowOperation(String operation, Node... steps) {
        return new Node()
                .type("Conversation/Sequential Workflow Operation")
                .properties("operation", new Node().value(operation))
                .properties("steps", new Node().items(steps));
    }

    private static Node directWorkflow(String channel, Node... steps) {
        return new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value(channel))
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

    private static Node triggerEventStep(Node event) {
        return new Node()
                .type("Conversation/Trigger Event")
                .properties("event", event);
    }

    private static Node operationRequestEventNode(String operation, Node request) {
        return new Node()
                .type("Conversation/Operation Request")
                .properties("operation", new Node().value(operation))
                .properties("request", request);
    }

    private static DocumentProcessingResult processChat(Fixture fixture,
                                                        Node document,
                                                        String timelineId,
                                                        int timestamp) {
        return fixture.blue.processDocument(document, chatTimelineEntry(fixture, timelineId, timestamp));
    }

    private static Node processOperationRequest(Fixture fixture,
                                                Node document,
                                                String timelineId,
                                                int timestamp,
                                                String operation,
                                                Node request) {
        return processOperationRequest(fixture, document, timelineId, timestamp, operation, request, null);
    }

    private static Node processOperationRequest(Fixture fixture,
                                                Node document,
                                                String timelineId,
                                                int timestamp,
                                                String operation,
                                                Node request,
                                                String sourceValue) {
        return processOperationRequest(fixture,
                document,
                timelineId,
                timestamp,
                operationRequestEventNode(operation, request),
                sourceValue);
    }

    private static Node processOperationRequest(Fixture fixture,
                                                Node document,
                                                String timelineId,
                                                int timestamp,
                                                Node operationRequest) {
        return processOperationRequest(fixture, document, timelineId, timestamp, operationRequest, null);
    }

    private static Node processOperationRequest(Fixture fixture,
                                                Node document,
                                                String timelineId,
                                                int timestamp,
                                                Node operationRequest,
                                                String sourceValue) {
        return fixture.blue.processDocument(document,
                operationRequestTimelineEntry(fixture, timelineId, timestamp, operationRequest, sourceValue)).document();
    }

    private static Node operationRequestTimelineEntry(Fixture fixture,
                                                      String timelineId,
                                                      int timestamp,
                                                      Node operationRequest,
                                                      String sourceValue) {
        Node event = new Node()
                .blue(fixture.repository.typeAliasBlue())
                .type("Conversation/Timeline Entry")
                .properties("timeline", new Node()
                        .properties("timelineId", new Node().value(timelineId)))
                .properties("timestamp", new Node().value(timestamp))
                .properties("message", operationRequest);
        if (sourceValue != null) {
            event.properties("source", new Node()
                    .properties("value", new Node().value(sourceValue)));
        }
        return fixture.blue.preprocess(event);
    }

    private static Node chatTimelineEntry(Fixture fixture, String timelineId, int timestamp) {
        Node event = new Node()
                .blue(fixture.repository.typeAliasBlue())
                .type("Conversation/Timeline Entry")
                .properties("timeline", new Node()
                        .properties("timelineId", new Node().value(timelineId)))
                .properties("timestamp", new Node().value(timestamp))
                .properties("message", new Node()
                        .type("Conversation/Chat Message")
                        .properties("message", new Node().value("run")));
        return fixture.blue.preprocess(event);
    }

    private static Node largePayloadBranch() {
        Node root = new Node();
        for (int i = 0; i < 12; i++) {
            root.properties("branch" + i, new Node()
                    .properties("value", new Node().value(i))
                    .properties("nested", new Node()
                            .properties("ignored", new Node().value("payload-" + i))));
        }
        return root;
    }

    private static Node document(BlueRepository repository, int counter, Map<String, Node> contracts) {
        return new Node()
                .blue(repository.typeAliasBlue())
                .name("Operation Request Test")
                .properties("counter", new Node().value(counter))
                .properties("contracts", new Node().properties(contracts));
    }

    private static Node initializedDocument(Fixture fixture, Node document) {
        return fixture.blue.initializeDocument(fixture.blue.preprocess(document)).document();
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        BlueDocumentProcessors.registerWith(blue);
        TestTimelineProvider.registerWith(blue);
        return new Fixture(repository, blue);
    }

    private static void assertCounter(Node document, int expected) {
        assertEquals(BigInteger.valueOf(expected), document.get("/counter"));
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
