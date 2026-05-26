package blue.contract.processor.conversation;

import blue.contract.processor.BlueDocumentProcessors;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.repo.BlueRepository;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreRuntimeChannelsTest {

    @Test
    void realCoreDocumentUpdateChannelReceivesUpdateEvents() {
        Fixture fixture = configuredFixture();
        Map<String, Node> contracts = ownerChannelContracts();
        contracts.put("updates", documentUpdateChannel("/counter"));
        contracts.put("writer", directWorkflow("owner", updateDocumentStep("replace", "/counter", new Node().value(5))));
        contracts.put("observer", directWorkflowMatching("updates",
                new Node().type("Core/Document Update"),
                triggerEventStep(chatMessageEvent("updated ${event.path} from ${event.before} to ${event.after}"))));
        Node document = initializedDocument(fixture, document(fixture.repository, 0, contracts));

        DocumentProcessingResult result = processChat(fixture, document, 1);

        assertEquals(BigInteger.valueOf(5), result.document().get("/counter"));
        assertContainsChatMessage(result.triggeredEvents(), "updated /counter from 0 to 5");
    }

    @Test
    void documentUpdateChannelPathFilteringUsesRepositoryCoreTypes() {
        Fixture fixture = configuredFixture();
        Map<String, Node> contracts = ownerChannelContracts();
        contracts.put("counterUpdates", documentUpdateChannel("/counter"));
        contracts.put("nameUpdates", documentUpdateChannel("/name"));
        contracts.put("writer", directWorkflow("owner", updateDocumentStep("replace", "/counter", new Node().value(5))));
        contracts.put("counterObserver", directWorkflowMatching("counterUpdates",
                new Node().type("Core/Document Update"),
                triggerEventStep(chatMessageEvent("counter updated"))));
        contracts.put("nameObserver", directWorkflowMatching("nameUpdates",
                new Node().type("Core/Document Update"),
                triggerEventStep(chatMessageEvent("name updated"))));
        Node document = initializedDocument(fixture, document(fixture.repository, 0, contracts));

        DocumentProcessingResult result = processChat(fixture, document, 1);

        assertContainsChatMessage(result.triggeredEvents(), "counter updated");
        assertNoChatMessage(result.triggeredEvents(), "name updated");
    }

    @Test
    void nestedUpdatesPropagateToParentWatchers() {
        Fixture fixture = configuredFixture();
        Map<String, Node> contracts = ownerChannelContracts();
        contracts.put("profileUpdates", documentUpdateChannel("/profile"));
        contracts.put("writer", directWorkflow("owner",
                updateDocumentStep("replace", "/profile/name", new Node().value("Ada"))));
        contracts.put("observer", directWorkflowMatching("profileUpdates",
                new Node().type("Core/Document Update"),
                triggerEventStep(chatMessageEvent("updated ${event.path} from ${event.before} to ${event.after}"))));
        Node document = document(fixture.repository, 0, contracts)
                .properties("profile", new Node()
                        .properties("name", new Node().value("Grace")));
        Node initialized = initializedDocument(fixture, document);

        DocumentProcessingResult result = processChat(fixture, initialized, 1);

        assertEquals("Ada", result.document()
                .getProperties().get("profile")
                .getProperties().get("name")
                .getValue());
        assertContainsChatMessage(result.triggeredEvents(), "updated /profile/name from Grace to Ada");
    }

    @Test
    void updateEventCanBeMatchedMoreSpecifically() {
        Fixture fixture = configuredFixture();
        Map<String, Node> contracts = ownerChannelContracts();
        contracts.put("allUpdates", documentUpdateChannel("/"));
        contracts.put("writer", directWorkflow("owner",
                updateDocumentStep("replace", "/counter", new Node().value(5)),
                updateDocumentStep("add", "/other", new Node().value(9))));
        contracts.put("observer", directWorkflowMatching("allUpdates",
                new Node()
                        .type("Core/Document Update")
                        .properties("path", new Node().value("/counter"))
                        .properties("op", new Node().value("replace")),
                triggerEventStep(chatMessageEvent("specific replace"))));
        Node document = initializedDocument(fixture, document(fixture.repository, 0, contracts));

        DocumentProcessingResult result = processChat(fixture, document, 1);

        assertEquals(BigInteger.valueOf(5), result.document().get("/counter"));
        assertEquals(BigInteger.valueOf(9), result.document().get("/other"));
        assertSingleChatMessage(result.triggeredEvents(), "specific replace");
    }

    @Test
    void embeddedChildProcessesExternalEventWithRealProcessEmbeddedType() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, embeddedOperationDocument(fixture.repository));

        DocumentProcessingResult result = fixture.blue.processDocument(document,
                operationRequestEvent(fixture, 1, "increment", new Node().value(7)));

        assertEquals(BigInteger.valueOf(100), result.document().get("/counter"));
        assertEquals(BigInteger.valueOf(7), result.document().get("/child/counter"));
    }

    @Test
    void parentCannotPatchIntoEmbeddedScope() {
        Fixture fixture = configuredFixture();
        Map<String, Node> contracts = ownerChannelContracts();
        contracts.put("embedded", processEmbedded("/child"));
        contracts.put("writer", directWorkflow("owner",
                updateDocumentStep("replace", "/child/counter", new Node().value(99))));
        Node document = initializedDocument(fixture, document(fixture.repository, 0, contracts)
                .properties("child", childDocument(1, new LinkedHashMap<String, Node>())));

        DocumentProcessingResult result = processChat(fixture, document, 1);

        assertEquals(BigInteger.valueOf(1), result.document().get("/child/counter"));
        assertEquals("fatal", result.document().get("/contracts/terminated/cause"));
    }

    @Test
    void replacingEmbeddedNodeCutsOffChildScopeWithinRun() {
        Fixture fixture = configuredFixture();
        Map<String, Node> childContracts = ownerChannelContracts();
        childContracts.put("probe", directWorkflow("owner",
                triggerEventStep(chatMessageEvent("pre-cutoff")),
                updateDocumentStep("replace", "/marker", new Node().value(1)),
                triggerEventStep(chatMessageEvent("post-cutoff"))));

        Map<String, Node> rootContracts = ownerChannelContracts();
        rootContracts.put("embedded", processEmbedded("/child"));
        rootContracts.put("childUpdates", documentUpdateChannel("/child/marker"));
        rootContracts.put("cutChild", directWorkflowMatching("childUpdates",
                new Node().type("Core/Document Update"),
                updateDocumentStep("replace", "/child", new Node()
                        .name("Replacement Child")
                        .properties("counter", new Node().value(0)))));
        Node document = initializedDocument(fixture, document(fixture.repository, 0, rootContracts)
                .properties("child", childDocument(0, childContracts)));

        DocumentProcessingResult result = processChat(fixture, document, 1);

        assertEquals("Replacement Child", nodeAt(result.document(), "/child").getName());
        assertNull(nodeAt(result.document(), "/child/marker"));
        assertNoChatMessage(result.triggeredEvents(), "post-cutoff");
    }

    @Test
    void embeddedNodeChannelBridgesConfiguredChildEmissions() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, embeddedBridgeDocument(fixture.repository, "/child"));

        DocumentProcessingResult result = processChat(fixture, document, 1);

        assertContainsChatMessage(result.triggeredEvents(), "parent saw child emitted");
        assertNoChatMessage(result.triggeredEvents(), "parent saw other child emitted");
    }

    @Test
    void embeddedNodeChannelDoesNotBridgeWrongChildPath() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, embeddedBridgeDocument(fixture.repository, "/missingChild"));

        DocumentProcessingResult result = processChat(fixture, document, 1);

        assertNoChatMessage(result.triggeredEvents(), "parent saw child emitted");
        assertNoChatMessage(result.triggeredEvents(), "parent saw other child emitted");
    }

    @Test
    void duplicateExternalEventsAreSkippedWithRealRepositoryChannelCheckpointShape() {
        Fixture fixture = configuredFixture();
        Map<String, Node> contracts = ownerChannelContracts();
        contracts.put("writer", directWorkflow("owner",
                updateDocumentStep("replace", "/counter", new Node().value("${document('/counter') + 1}"))));
        Node initialized = initializedDocument(fixture, document(fixture.repository, 0, contracts));
        Node event = chatTimelineEntry(fixture, 1);

        Node afterFirst = fixture.blue.processDocument(initialized, event).document();
        Node afterSecond = fixture.blue.processDocument(afterFirst, event).document();

        assertEquals(BigInteger.ONE, afterSecond.get("/counter"));
        Node checkpoint = nodeAt(afterSecond, "/contracts/checkpoint");
        assertNotNull(checkpoint);
        assertNotNull(nodeAt(checkpoint, "/lastEvents/owner"));
    }

    @Test
    void checkpointDeclaredUnderWrongKeyFails() {
        Fixture fixture = configuredFixture();
        Map<String, Node> contracts = ownerChannelContracts();
        contracts.put("wrongCheckpoint", new Node().type("Core/Channel Event Checkpoint"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fixture.blue.initializeDocument(fixture.blue.preprocess(document(fixture.repository, 0, contracts))));

        assertTrue(ex.getMessage().contains("Channel Event Checkpoint"));
    }

    @Test
    void multipleCheckpointMarkersInOneScopeFail() {
        Fixture fixture = configuredFixture();
        Map<String, Node> contracts = ownerChannelContracts();
        Node initialized = initializedDocument(fixture, document(fixture.repository, 0, contracts));
        initialized.getContracts().properties("checkpoint", new Node()
                .type(new Node().blueId(blue.repo.core.ChannelEventCheckpoint.blueId()))
                .properties("lastEvents", new Node().properties(new LinkedHashMap<String, Node>())));
        initialized.getContracts().properties("extraCheckpoint", new Node()
                .type(new Node().blueId(blue.repo.core.ChannelEventCheckpoint.blueId())));

        assertThrows(RuntimeException.class,
                () -> fixture.blue.processDocument(fixture.blue.preprocess(initialized), chatTimelineEntry(fixture, 1)));
    }

    private static Node embeddedOperationDocument(BlueRepository repository) {
        Map<String, Node> childContracts = ownerChannelContracts();
        childContracts.put("increment", operation("owner"));
        childContracts.put("incrementImpl", sequentialWorkflowOperation("increment",
                updateDocumentStep("replace", "/counter",
                        new Node().value("${event.message.request + document('/counter')}"))));

        Map<String, Node> rootContracts = new LinkedHashMap<String, Node>();
        rootContracts.put("embedded", processEmbedded("/child"));
        return document(repository, 100, rootContracts)
                .properties("child", childDocument(0, childContracts));
    }

    private static Node embeddedBridgeDocument(BlueRepository repository, String childPath) {
        Map<String, Node> childContracts = ownerChannelContracts();
        childContracts.put("emit", directWorkflow("owner", triggerEventStep(chatMessageEvent("child emitted"))));

        Map<String, Node> otherChildContracts = ownerChannelContracts();
        otherChildContracts.put("emit", directWorkflow("owner", triggerEventStep(chatMessageEvent("other child emitted"))));

        Map<String, Node> rootContracts = ownerChannelContracts();
        rootContracts.put("embedded", new Node()
                .type("Core/Process Embedded")
                .properties("paths", new Node().items(
                        new Node().value("/child"),
                        new Node().value("/otherChild"))));
        rootContracts.put("embeddedEvents", new Node()
                .type("Core/Embedded Node Channel")
                .properties("childPath", new Node().value(childPath)));
        rootContracts.put("childObserver", directWorkflowMatching("embeddedEvents",
                new Node()
                        .type("Conversation/Chat Message")
                        .properties("message", new Node().value("child emitted")),
                triggerEventStep(chatMessageEvent("parent saw child emitted"))));
        rootContracts.put("otherChildObserver", directWorkflowMatching("embeddedEvents",
                new Node()
                        .type("Conversation/Chat Message")
                        .properties("message", new Node().value("other child emitted")),
                triggerEventStep(chatMessageEvent("parent saw other child emitted"))));
        return document(repository, 0, rootContracts)
                .properties("child", childDocument(0, childContracts))
                .properties("otherChild", childDocument(0, otherChildContracts));
    }

    private static Map<String, Node> ownerChannelContracts() {
        Map<String, Node> contracts = new LinkedHashMap<String, Node>();
        contracts.put("owner", TestTimelineProvider.channel("owner"));
        return contracts;
    }

    private static Node documentUpdateChannel(String path) {
        return new Node()
                .type("Core/Document Update Channel")
                .properties("path", new Node().value(path));
    }

    private static Node processEmbedded(String path) {
        return new Node()
                .type("Core/Process Embedded")
                .properties("paths", new Node().items(new Node().value(path)));
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

    private static Node directWorkflow(String channel, Node... steps) {
        Node workflow = new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value(channel))
                .properties("steps", new Node().items(steps));
        return workflow;
    }

    private static Node directWorkflowMatching(String channel, Node event, Node... steps) {
        Node workflow = new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value(channel))
                .properties("steps", new Node().items(steps));
        workflow.properties("event", event);
        return workflow;
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

    private static Node chatMessageEvent(String message) {
        return new Node()
                .type("Conversation/Chat Message")
                .properties("message", new Node().value(message));
    }

    private static Node childDocument(int counter, Map<String, Node> contracts) {
        Node child = new Node()
                .name("Child")
                .properties("counter", new Node().value(counter));
        if (!contracts.isEmpty()) {
            child.properties("contracts", new Node().properties(contracts));
        }
        return child;
    }

    private static Node document(BlueRepository repository, int counter, Map<String, Node> contracts) {
        return new Node()
                .blue(repository.typeAliasBlue())
                .name("Core Runtime Test")
                .properties("counter", new Node().value(counter))
                .properties("contracts", new Node().properties(contracts));
    }

    private static Node initializedDocument(Fixture fixture, Node document) {
        return fixture.blue.initializeDocument(fixture.blue.preprocess(document)).document();
    }

    private static DocumentProcessingResult processChat(Fixture fixture, Node document, int timestamp) {
        return fixture.blue.processDocument(document, chatTimelineEntry(fixture, timestamp));
    }

    private static Node chatTimelineEntry(Fixture fixture, int timestamp) {
        Node event = new Node()
                .blue(fixture.repository.typeAliasBlue())
                .type("Conversation/Timeline Entry")
                .properties("timeline", new Node()
                        .properties("timelineId", new Node().value("owner")))
                .properties("timestamp", new Node().value(timestamp))
                .properties("message", chatMessageEvent("run"));
        return fixture.blue.preprocess(event).blue(null);
    }

    private static Node operationRequestEvent(Fixture fixture,
                                              int timestamp,
                                              String operation,
                                              Node request) {
        Node event = new Node()
                .blue(fixture.repository.typeAliasBlue())
                .type("Conversation/Timeline Entry")
                .properties("timeline", new Node()
                        .properties("timelineId", new Node().value("owner")))
                .properties("timestamp", new Node().value(timestamp))
                .properties("message", new Node()
                        .type("Conversation/Operation Request")
                        .properties("operation", new Node().value(operation))
                        .properties("request", request));
        return fixture.blue.preprocess(event).blue(null);
    }

    private static Node nodeAt(Node node, String pointer) {
        try {
            Object value = node.get(pointer);
            return value instanceof Node ? (Node) value : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = ConversationTestResources.configuredBlue(repository);
        BlueDocumentProcessors.registerWith(blue);
        TestTimelineProvider.registerWith(blue);
        return new Fixture(repository, blue);
    }

    private static void assertSingleChatMessage(List<Node> events, String expectedMessage) {
        int count = 0;
        for (Node event : events) {
            if (isChatMessage(event, expectedMessage)) {
                count++;
            }
        }
        assertEquals(1, count, "Expected exactly one chat message: " + expectedMessage);
    }

    private static void assertContainsChatMessage(List<Node> events, String expectedMessage) {
        for (Node event : events) {
            if (isChatMessage(event, expectedMessage)) {
                return;
            }
        }
        assertFalse(true, "Expected chat message: " + expectedMessage);
    }

    private static void assertNoChatMessage(List<Node> events, String message) {
        for (Node event : events) {
            assertFalse(isChatMessage(event, message), "Unexpected chat message: " + message);
        }
    }

    private static boolean isChatMessage(Node event, String message) {
        try {
            return message.equals(event.get("/message"));
        } catch (IllegalArgumentException ex) {
            return false;
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
