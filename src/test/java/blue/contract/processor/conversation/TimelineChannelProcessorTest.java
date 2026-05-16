package blue.contract.processor.conversation;

import blue.contract.processor.BlueDocumentProcessors;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.repo.BlueRepository;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimelineChannelProcessorTest {

    @Test
    void matchesTimelineEntryForConfiguredTimeline() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, "owner");
        Node event = timelineEntryEvent(fixture, "owner", 10, "hello");

        Node processed = fixture.blue.processDocument(document, event).document();

        assertEquals("owner", checkpointEvent(processed)
                .getAsText("/timeline/timelineId"));
        assertEquals("hello", checkpointEvent(processed)
                .getAsText("/message/message"));
    }

    @Test
    void rejectsDifferentTimeline() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, "owner");
        Node event = timelineEntryEvent(fixture, "other", 10, "hello");

        Node processed = fixture.blue.processDocument(document, event).document();

        assertNull(checkpointEvent(processed));
    }

    @Test
    void channelWithoutTimelineIdMatchesAnyTimelineEntry() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, null);
        Node event = timelineEntryEvent(fixture, "any", 10, "hello");

        Node processed = fixture.blue.processDocument(document, event).document();

        assertNotNull(checkpointEvent(processed));
        assertEquals("any", checkpointEvent(processed).getAsText("/timeline/timelineId"));
    }

    @Test
    void rejectsNonTimelineEntry() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, "owner");
        Node event = chatMessageEvent(fixture, "hello");

        Node processed = fixture.blue.processDocument(document, event).document();

        assertNull(checkpointEvent(processed));
    }

    @Test
    void rejectsExplicitNonTimelineEntryEvenWithTimelineEntryShape() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, "owner");
        Node event = misleadingChatMessageEvent(fixture);

        Node processed = fixture.blue.processDocument(document, event).document();

        assertNull(checkpointEvent(processed));
    }

    @Test
    void rejectsStaleTimelineEntry() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, "owner");
        Node first = fixture.blue.processDocument(document,
                timelineEntryEvent(fixture, "owner", 10, "newer")).document();

        Node afterStale = fixture.blue.processDocument(first,
                timelineEntryEvent(fixture, "owner", 9, "stale")).document();

        assertEquals(new BigInteger("10"), checkpointEvent(afterStale).get("/timestamp"));
        assertEquals("newer", checkpointEvent(afterStale).getAsText("/message/message"));
    }

    @Test
    void acceptsNewerTimelineEntry() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, "owner");
        Node first = fixture.blue.processDocument(document,
                timelineEntryEvent(fixture, "owner", 10, "first")).document();

        Node afterNewer = fixture.blue.processDocument(first,
                timelineEntryEvent(fixture, "owner", 11, "second")).document();

        assertEquals(new BigInteger("11"), checkpointEvent(afterNewer).get("/timestamp"));
        assertEquals("second", checkpointEvent(afterNewer).getAsText("/message/message"));
    }

    @Test
    void acceptsDifferentEntryWithEqualTimestamp() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, "owner");
        Node first = fixture.blue.processDocument(document,
                timelineEntryEvent(fixture, "owner", 10, "first")).document();

        Node afterEqual = fixture.blue.processDocument(first,
                timelineEntryEvent(fixture, "owner", 10, "equal")).document();

        assertEquals(new BigInteger("10"), checkpointEvent(afterEqual).get("/timestamp"));
        assertEquals("equal", checkpointEvent(afterEqual).getAsText("/message/message"));
    }

    @Test
    void duplicateSameTimelineEntryIsIgnored() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, "owner");
        Node event = timelineEntryEvent(fixture, "owner", 10, "hello");
        Node afterFirst = fixture.blue.processDocument(document, event).document();
        String signatureBefore = checkpointSignature(afterFirst);

        Node afterSecond = fixture.blue.processDocument(afterFirst, event).document();
        String signatureAfter = checkpointSignature(afterSecond);

        assertEquals(signatureBefore, signatureAfter);
        assertEquals("hello", checkpointEvent(afterSecond).getAsText("/message/message"));
    }

    private static Node initializedDocument(Fixture fixture, String timelineId) {
        Node document = timelineDocument(fixture.repository, timelineId);
        DocumentProcessingResult result = fixture.blue.initializeDocument(fixture.blue.preprocess(document));
        return result.document();
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        BlueDocumentProcessors.registerWith(blue);
        TestTimelineProvider.registerWith(blue);
        return new Fixture(repository, blue);
    }

    private static Node timelineDocument(BlueRepository repository, String timelineId) {
        Node ownerChannel = TestTimelineProvider.channel(timelineId);
        Map<String, Node> contracts = new LinkedHashMap<>();
        contracts.put("ownerChannel", ownerChannel);

        return new Node()
                .blue(repository.typeAliasBlue())
                .name("Timeline Test")
                .properties("contracts", new Node().properties(contracts));
    }

    private static Node timelineEntryEvent(Fixture fixture, String timelineId, int timestamp, String message) {
        return TestTimelineProvider.timelineEntry(fixture.blue,
                fixture.repository,
                timelineId,
                timestamp,
                TestTimelineProvider.chatMessage(message));
    }

    private static Node chatMessageEvent(Fixture fixture, String message) {
        Node event = new Node()
                .blue(fixture.repository.typeAliasBlue())
                .type("Conversation/Chat Message")
                .properties("message", new Node().value(message));
        return fixture.blue.preprocess(event);
    }

    private static Node misleadingChatMessageEvent(Fixture fixture) {
        Node event = new Node()
                .blue(fixture.repository.typeAliasBlue())
                .type("Conversation/Chat Message")
                .properties("timeline", new Node()
                        .properties("timelineId", new Node().value("owner")))
                .properties("timestamp", new Node().value(10))
                .properties("message", new Node().value("misleading"));
        return fixture.blue.preprocess(event);
    }

    private static Node checkpointEvent(Node document) {
        Node checkpoint = checkpoint(document);
        if (checkpoint == null) {
            return null;
        }
        Node lastEvents = property(checkpoint, "lastEvents");
        return property(lastEvents, "ownerChannel");
    }

    private static Node checkpoint(Node document) {
        Node contracts = property(document, "contracts");
        return property(contracts, "checkpoint");
    }

    private static String checkpointSignature(Node document) {
        Node checkpoint = checkpoint(document);
        Node lastSignatures = property(checkpoint, "lastSignatures");
        Node signature = property(lastSignatures, "ownerChannel");
        Object value = signature != null ? signature.getValue() : null;
        return value instanceof String ? (String) value : null;
    }

    private static Node property(Node node, String key) {
        if (node == null || node.getProperties() == null) {
            return null;
        }
        return node.getProperties().get(key);
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
