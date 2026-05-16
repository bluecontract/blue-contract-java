package blue.contract.processor.myos;

import blue.contract.processor.BlueDocumentProcessors;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.repo.BlueRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MyOSTimelineChannelProcessorTest {
    private static final String TIMELINE_ID = "bb13b2d9-3df9-5fea-9fdf-dd4f0ae74486";
    private static final String ACCOUNT_ID = "bbe140c4-7625-41cd-9381-1f677014e996";
    private static final String EMAIL = "alice@example.com";

    @Test
    void matchesTimelineAndAccount() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, document(fixture.repository,
                myosChannel(TIMELINE_ID, ACCOUNT_ID, null)));

        Node processed = fixture.blue.processDocument(initialized,
                myosTimelineEntry(fixture, TIMELINE_ID, ACCOUNT_ID, null, 1777987926951095L, "hello")).document();

        assertNotNull(checkpointEvent(processed));
        assertEquals(TIMELINE_ID, checkpointEvent(processed).getAsText("/timeline/timelineId"));
        assertEquals("hello", checkpointEvent(processed).getAsText("/message/message"));
    }

    @Test
    void rejectsDifferentAccountForSameTimeline() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, document(fixture.repository,
                myosChannel(TIMELINE_ID, ACCOUNT_ID, null)));

        Node processed = fixture.blue.processDocument(initialized,
                myosTimelineEntry(fixture, TIMELINE_ID, "other-account", null, 1L, "hello")).document();

        assertNull(checkpointEvent(processed));
    }

    @Test
    void rejectsDifferentTimelineForSameAccount() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, document(fixture.repository,
                myosChannel(TIMELINE_ID, ACCOUNT_ID, null)));

        Node processed = fixture.blue.processDocument(initialized,
                myosTimelineEntry(fixture, "other-timeline", ACCOUNT_ID, null, 1L, "hello")).document();

        assertNull(checkpointEvent(processed));
    }

    @Test
    void rejectsMissingRequiredAccount() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, document(fixture.repository,
                myosChannel(TIMELINE_ID, ACCOUNT_ID, null)));

        Node processed = fixture.blue.processDocument(initialized,
                myosTimelineEntry(fixture, TIMELINE_ID, null, null, 1L, "hello")).document();

        assertNull(checkpointEvent(processed));
    }

    @Test
    void rejectsDifferentOrMissingRequiredEmail() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, document(fixture.repository,
                myosChannel(TIMELINE_ID, null, EMAIL)));

        Node differentEmail = fixture.blue.processDocument(initialized,
                myosTimelineEntry(fixture, TIMELINE_ID, null, "other@example.com", 1L, "hello")).document();
        Node missingEmail = fixture.blue.processDocument(initialized,
                myosTimelineEntry(fixture, TIMELINE_ID, null, null, 2L, "hello")).document();

        assertNull(checkpointEvent(differentEmail));
        assertNull(checkpointEvent(missingEmail));
    }

    @Test
    void matchesByTimelineOnlyWhenNoActorConstraintsAreDeclared() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, document(fixture.repository,
                myosChannel(TIMELINE_ID, null, null)));

        Node processed = fixture.blue.processDocument(initialized,
                myosTimelineEntry(fixture, TIMELINE_ID, "any-account", "any@example.com", 1L, "hello")).document();

        assertNotNull(checkpointEvent(processed));
    }

    private static Node document(BlueRepository repository, Node ownerChannel) {
        Map<String, Node> contracts = new LinkedHashMap<String, Node>();
        contracts.put("ownerChannel", ownerChannel);
        return new Node()
                .blue(repository.typeAliasBlue())
                .name("MyOS Timeline Test")
                .properties("contracts", new Node().properties(contracts));
    }

    private static Node myosChannel(String timelineId, String accountId, String email) {
        Node channel = new Node()
                .type("MyOS/MyOS Timeline Channel")
                .properties("timelineId", new Node().value(timelineId));
        if (accountId != null) {
            channel.properties("accountId", new Node().value(accountId));
        }
        if (email != null) {
            channel.properties("email", new Node().value(email));
        }
        return channel;
    }

    private static Node myosTimelineEntry(Fixture fixture,
                                          String timelineId,
                                          String accountId,
                                          String email,
                                          long timestamp,
                                          String message) {
        Node actor = new Node().type("MyOS/Principal Actor");
        if (accountId != null) {
            actor.properties("accountId", new Node().value(accountId));
        }
        if (email != null) {
            actor.properties("email", new Node().value(email));
        }
        Node event = new Node()
                .blue(fixture.repository.typeAliasBlue())
                .type("MyOS/MyOS Timeline Entry")
                .properties("timeline", new Node()
                        .properties("timelineId", new Node().value(timelineId)))
                .properties("timestamp", new Node().value(timestamp))
                .properties("actor", actor)
                .properties("message", new Node()
                        .type("Conversation/Chat Message")
                        .properties("message", new Node().value(message)));
        return fixture.blue.preprocess(event);
    }

    private static Node initializedDocument(Fixture fixture, Node document) {
        DocumentProcessingResult result = fixture.blue.initializeDocument(fixture.blue.preprocess(document));
        return result.document();
    }

    private static Node checkpointEvent(Node document) {
        Node contracts = property(document, "contracts");
        Node checkpoint = property(contracts, "checkpoint");
        Node lastEvents = property(checkpoint, "lastEvents");
        return property(lastEvents, "ownerChannel");
    }

    private static Node property(Node node, String key) {
        if (node == null || node.getProperties() == null) {
            return null;
        }
        return node.getProperties().get(key);
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        BlueDocumentProcessors.registerWith(blue);
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
