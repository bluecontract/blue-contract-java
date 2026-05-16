package blue.contract.processor;

import blue.contract.processor.conversation.TestTimelineProvider;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.repo.BlueRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MustUnderstandContractsTest {
    @Test
    void unknownContractTypeStopsInitialization() {
        Fixture fixture = configuredFixture(false);
        Node document = document(fixture.repository, contract("unknown", new Node()
                .type(new Node().blueId("unknown-contract-blue-id"))));

        DocumentProcessingResult result = initialize(fixture, document);

        assertCapabilityFailure(result, "Unsupported contract type");
    }

    @Test
    void coreChannelContractStopsInitializationWhenUsedAsExecutableContract() {
        Fixture fixture = configuredFixture(false);
        Node document = document(fixture.repository, contract("owner", new Node().type("Core/Channel")));

        DocumentProcessingResult result = initialize(fixture, document);

        assertCapabilityFailure(result, "Unsupported contract type");
    }

    @Test
    void abstractConversationTimelineChannelStopsInitializationWhenUsedDirectly() {
        Fixture fixture = configuredFixture(false);
        Node document = document(fixture.repository, contract("owner", new Node()
                .type("Conversation/Timeline Channel")
                .properties("timelineId", new Node().value("owner"))));

        DocumentProcessingResult result = initialize(fixture, document);

        assertCapabilityFailure(result, "Unsupported contract type");
    }

    @Test
    void handlerBoundToAbstractTimelineChannelFailsClearly() {
        Fixture fixture = configuredFixture(false);
        Map<String, Node> contracts = contract("owner", new Node()
                .type("Conversation/Timeline Channel")
                .properties("timelineId", new Node().value("owner")));
        contracts.put("handler", new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("owner"))
                .properties("steps", new Node().items()));
        Node document = document(fixture.repository, contracts);

        DocumentProcessingResult result = initialize(fixture, document);

        assertCapabilityFailure(result, "Unsupported contract type");
    }

    @Test
    void handlerBoundToTypelessContractFailsClearly() {
        Fixture fixture = configuredFixture(false);
        Map<String, Node> contracts = contract("owner", new Node()
                .properties("timelineId", new Node().value("owner")));
        contracts.put("handler", new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("owner"))
                .properties("steps", new Node().items()));
        Node document = document(fixture.repository, contracts);

        DocumentProcessingResult result = initialize(fixture, document);

        assertCapabilityFailure(result, "must declare a type");
    }

    @Test
    void simpleTimelineProviderWorksWhenRegistered() {
        Fixture fixture = configuredFixture(true);
        Node document = document(fixture.repository, contract("owner", TestTimelineProvider.channel("owner")));
        Node initialized = initialize(fixture, document).document();

        DocumentProcessingResult result = fixture.blue.processDocument(initialized,
                TestTimelineProvider.timelineEntry(fixture.blue,
                        fixture.repository,
                        "owner",
                        1,
                        TestTimelineProvider.chatMessage("hello")));

        assertFalse(result.capabilityFailure(), result.failureReason());
        assertNotNull(checkpointEvent(result.document(), "owner"));
    }

    @Test
    void myosTimelineProviderWorksByDefault() {
        Fixture fixture = configuredFixture(false);
        Node document = document(fixture.repository, contract("owner", new Node()
                .type("MyOS/MyOS Timeline Channel")
                .properties("timelineId", new Node().value("owner"))
                .properties("accountId", new Node().value("account"))));
        Node initialized = initialize(fixture, document).document();

        DocumentProcessingResult result = fixture.blue.processDocument(initialized,
                myosTimelineEntry(fixture, "owner", "account", 1));

        assertFalse(result.capabilityFailure(), result.failureReason());
        assertNotNull(checkpointEvent(result.document(), "owner"));
    }

    private static DocumentProcessingResult initialize(Fixture fixture, Node document) {
        return fixture.blue.initializeDocument(fixture.blue.preprocess(document));
    }

    private static void assertCapabilityFailure(DocumentProcessingResult result, String reason) {
        assertTrue(result.capabilityFailure(), result.failureReason());
        assertTrue(result.failureReason().contains(reason), result.failureReason());
        assertEquals(0L, result.totalGas());
        assertTrue(result.triggeredEvents().isEmpty());
        assertFalse(hasInitializedMarker(result.document()));
    }

    private static boolean hasInitializedMarker(Node document) {
        Node contracts = property(document, "contracts");
        return property(contracts, "initialized") != null;
    }

    private static Node checkpointEvent(Node document, String key) {
        Node contracts = property(document, "contracts");
        Node checkpoint = property(contracts, "checkpoint");
        Node lastEvents = property(checkpoint, "lastEvents");
        return property(lastEvents, key);
    }

    private static Node myosTimelineEntry(Fixture fixture, String timelineId, String accountId, int timestamp) {
        Node event = new Node()
                .blue(fixture.repository.typeAliasBlue())
                .type("MyOS/MyOS Timeline Entry")
                .properties("timeline", new Node()
                        .properties("timelineId", new Node().value(timelineId)))
                .properties("timestamp", new Node().value(timestamp))
                .properties("actor", new Node()
                        .type("MyOS/Principal Actor")
                        .properties("accountId", new Node().value(accountId)))
                .properties("message", TestTimelineProvider.chatMessage("hello"));
        return fixture.blue.preprocess(event);
    }

    private static Map<String, Node> contract(String key, Node contract) {
        Map<String, Node> contracts = new LinkedHashMap<String, Node>();
        contracts.put(key, contract);
        return contracts;
    }

    private static Node document(BlueRepository repository, Map<String, Node> contracts) {
        return new Node()
                .blue(repository.typeAliasBlue())
                .name("Must Understand Test")
                .properties("contracts", new Node().properties(contracts));
    }

    private static Node property(Node node, String key) {
        if (node == null || node.getProperties() == null) {
            return null;
        }
        return node.getProperties().get(key);
    }

    private static Fixture configuredFixture(boolean simpleTimelineProvider) {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        BlueDocumentProcessors.registerWith(blue);
        if (simpleTimelineProvider) {
            TestTimelineProvider.registerWith(blue);
        }
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
