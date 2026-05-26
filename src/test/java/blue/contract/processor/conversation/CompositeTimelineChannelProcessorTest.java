package blue.contract.processor.conversation;

import blue.contract.processor.BlueDocumentProcessors;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.ProcessorStatus;
import blue.repo.BlueRepository;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeTimelineChannelProcessorTest {

    @Test
    void deliversMatchingChildEvent() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, compositeDocument(fixture.repository,
                Arrays.asList("owner", "support"),
                timelineChannel("owner"),
                timelineChannel("support")));

        DocumentProcessingResult result = processChat(fixture, initialized, "owner", 1, "hello");

        assertChatCount(result.triggeredEvents(), "composite saw owner", 1);
        assertNotNull(nodeAt(result.document(), "/contracts/checkpoint/lastEvents/inbox::owner"));
    }

    @Test
    void doesNotDeliverNonMatchingChild() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, compositeDocument(fixture.repository,
                Arrays.asList("owner", "support"),
                timelineChannel("owner"),
                timelineChannel("support")));

        DocumentProcessingResult result = processChat(fixture, initialized, "unknown", 1, "hello");

        assertChatCount(result.triggeredEvents(), "composite saw owner", 0);
        assertChatCount(result.triggeredEvents(), "composite saw support", 0);
        assertNull(nodeAt(result.document(), "/contracts/checkpoint/lastEvents/inbox::owner"));
        assertNull(nodeAt(result.document(), "/contracts/checkpoint/lastEvents/inbox::support"));
    }

    @Test
    void deliversMultipleMatchingChildrenIndependently() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, compositeDocument(fixture.repository,
                Arrays.asList("childA", "childB"),
                timelineChannel("owner"),
                timelineChannel("owner")));

        DocumentProcessingResult result = processChat(fixture, initialized, "owner", 1, "hello");

        assertChatCount(result.triggeredEvents(), "composite saw childA", 1);
        assertChatCount(result.triggeredEvents(), "composite saw childB", 1);
        assertNotNull(nodeAt(result.document(), "/contracts/checkpoint/lastEvents/inbox::childA"));
        assertNotNull(nodeAt(result.document(), "/contracts/checkpoint/lastEvents/inbox::childB"));
    }

    @Test
    void duplicateEventIsSkippedPerChildCheckpoint() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, compositeDocument(fixture.repository,
                Arrays.asList("childA", "childB"),
                timelineChannel("owner"),
                timelineChannel("owner")));
        Node event = chatTimelineEntry(fixture, "owner", 1, "hello");

        DocumentProcessingResult first = fixture.blue.processDocument(initialized, event);
        Node firstA = nodeAt(first.document(), "/contracts/checkpoint/lastEvents/inbox::childA");
        Node firstB = nodeAt(first.document(), "/contracts/checkpoint/lastEvents/inbox::childB");
        DocumentProcessingResult second = fixture.blue.processDocument(first.document(), event);

        assertChatCount(first.triggeredEvents(), "composite saw childA", 1);
        assertChatCount(first.triggeredEvents(), "composite saw childB", 1);
        assertChatCount(second.triggeredEvents(), "composite saw childA", 0);
        assertChatCount(second.triggeredEvents(), "composite saw childB", 0);
        assertEquals(firstA.get("/timestamp"),
                nodeAt(second.document(), "/contracts/checkpoint/lastEvents/inbox::childA/timestamp").getValue());
        assertEquals(firstB.get("/timestamp"),
                nodeAt(second.document(), "/contracts/checkpoint/lastEvents/inbox::childB/timestamp").getValue());
    }

    @Test
    void childTimelineRecencyIsRespectedPerCompositeCheckpoint() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, compositeDocument(fixture.repository,
                Arrays.asList("owner"),
                timelineChannel("owner"),
                timelineChannel("support")));

        DocumentProcessingResult first = processChat(fixture, initialized, "owner", 10, "first");
        DocumentProcessingResult stale = processChat(fixture, first.document(), "owner", 9, "stale");
        DocumentProcessingResult newer = processChat(fixture, stale.document(), "owner", 11, "newer");

        assertChatCount(first.triggeredEvents(), "composite saw owner", 1);
        assertChatCount(stale.triggeredEvents(), "composite saw owner", 0);
        assertChatCount(newer.triggeredEvents(), "composite saw owner", 1);
        assertEquals(BigInteger.valueOf(10),
                stale.document().get("/contracts/checkpoint/lastEvents/inbox::owner/timestamp"));
        assertEquals(BigInteger.valueOf(11),
                newer.document().get("/contracts/checkpoint/lastEvents/inbox::owner/timestamp"));
    }

    @Test
    void missingChildChannelFailsClearly() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, compositeDocument(fixture.repository,
                Arrays.asList("missing"),
                timelineChannel("owner"),
                timelineChannel("support")));

        DocumentProcessingResult result = processChat(fixture, initialized, "owner", 1, "hello");

        assertRuntimeFatal(result, "Composite Timeline Channel");
        assertRuntimeFatal(result, "missing");
    }

    @Test
    void unsupportedChildChannelFailsClearly() {
        Fixture fixture = configuredFixture();
        Map<String, Node> contracts = new LinkedHashMap<String, Node>();
        contracts.put("owner", timelineChannel("owner"));
        contracts.put("triggered", new Node().type("Core/Triggered Event Channel"));
        contracts.put("inbox", compositeTimelineChannel(Arrays.asList("triggered")));
        contracts.put("handler", compositeHandler());
        Node initialized = initializedDocument(fixture, document(fixture.repository, contracts));

        DocumentProcessingResult result = processChat(fixture, initialized, "owner", 1, "hello");

        assertRuntimeFatal(result, "No processor registered");
    }

    @Test
    void selfReferenceFailsClearly() {
        Fixture fixture = configuredFixture();
        Node initialized = initializedDocument(fixture, compositeDocument(fixture.repository,
                Arrays.asList("inbox"),
                timelineChannel("owner"),
                timelineChannel("support")));

        DocumentProcessingResult result = processChat(fixture, initialized, "owner", 1, "hello");

        assertRuntimeFatal(result, "cannot include itself");
    }

    @Test
    void childChannelEventFilterIsHonored() {
        Fixture fixture = configuredFixture();
        Node filtered = timelineChannel("owner")
                .properties("event", new Node()
                        .type("Conversation/Timeline Entry")
                        .properties("message", new Node()
                                .type("Conversation/Chat Message")
                                .properties("message", new Node().value("allowed"))));
        Node initialized = initializedDocument(fixture, compositeDocument(fixture.repository,
                Arrays.asList("owner"),
                filtered,
                timelineChannel("support")));

        DocumentProcessingResult allowed = processChat(fixture, initialized, "owner", 1, "allowed");
        DocumentProcessingResult denied = processChat(fixture, allowed.document(), "owner", 2, "denied");

        assertChatCount(allowed.triggeredEvents(), "composite saw owner", 1);
        assertChatCount(denied.triggeredEvents(), "composite saw owner", 0);
    }

    private static Node compositeDocument(BlueRepository repository,
                                          List<String> children,
                                          Node firstChild,
                                          Node secondChild) {
        Map<String, Node> contracts = new LinkedHashMap<String, Node>();
        String firstKey = children.contains("childA") ? "childA" : "owner";
        String secondKey = children.contains("childB") ? "childB" : "support";
        contracts.put(firstKey, firstChild);
        contracts.put(secondKey, secondChild);
        contracts.put("inbox", compositeTimelineChannel(children));
        contracts.put("handler", compositeHandler());
        return document(repository, contracts);
    }

    private static Node document(BlueRepository repository, Map<String, Node> contracts) {
        return new Node()
                .blue(repository.typeAliasBlue())
                .name("Composite Timeline Test")
                .properties("contracts", new Node().properties(contracts));
    }

    private static Node timelineChannel(String timelineId) {
        return TestTimelineProvider.channel(timelineId);
    }

    private static Node compositeTimelineChannel(List<String> channels) {
        return new Node()
                .type("Conversation/Composite Timeline Channel")
                .properties("channels", new Node().items(channelNodes(channels)));
    }

    private static Node[] channelNodes(List<String> channels) {
        Node[] nodes = new Node[channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            nodes[i] = new Node().value(channels.get(i));
        }
        return nodes;
    }

    private static Node compositeHandler() {
        return new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("inbox"))
                .properties("steps", new Node().items(
                        triggerEventStep(chatMessageEvent("composite saw ${event.meta.compositeSourceChannelKey}"))));
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

    private static Node chatTimelineEntry(Fixture fixture, String timelineId, int timestamp, String message) {
        Node event = new Node()
                .blue(fixture.repository.typeAliasBlue())
                .type("Conversation/Timeline Entry")
                .properties("timeline", new Node()
                        .properties("timelineId", new Node().value(timelineId)))
                .properties("timestamp", new Node().value(timestamp))
                .properties("message", chatMessageEvent(message));
        return fixture.blue.preprocess(event).blue(null);
    }

    private static DocumentProcessingResult processChat(Fixture fixture,
                                                        Node document,
                                                        String timelineId,
                                                        int timestamp,
                                                        String message) {
        return fixture.blue.processDocument(document, chatTimelineEntry(fixture, timelineId, timestamp, message));
    }

    private static Node initializedDocument(Fixture fixture, Node document) {
        return fixture.blue.initializeDocument(fixture.blue.preprocess(document)).document();
    }

    private static Node nodeAt(Node node, String pointer) {
        if (node == null) {
            return null;
        }
        if ("/".equals(pointer)) {
            return node;
        }
        Node current = node;
        String[] segments = pointer.substring(1).split("/");
        for (String rawSegment : segments) {
            String segment = rawSegment.replace("~1", "/").replace("~0", "~");
            if ("contracts".equals(segment)) {
                current = current.getContracts();
            } else if (current.getProperties() != null && current.getProperties().containsKey(segment)) {
                current = current.getProperties().get(segment);
            } else if (current.getItems() != null && isArrayIndex(segment)) {
                int index = Integer.parseInt(segment);
                current = index < current.getItems().size() ? current.getItems().get(index) : null;
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static boolean isArrayIndex(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static void assertChatCount(List<Node> events, String message, int expected) {
        int count = 0;
        for (Node event : events) {
            try {
                if (message.equals(event.get("/message"))) {
                    count++;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        assertEquals(expected, count);
    }

    private static void assertRuntimeFatal(DocumentProcessingResult result, String expectedMessage) {
        assertEquals(ProcessorStatus.RUNTIME_FATAL, result.status(), result.failureReason());
        assertTrue(result.failureReason() != null && result.failureReason().contains(expectedMessage),
                result.failureReason());
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = ConversationTestResources.configuredBlue(repository);
        BlueDocumentProcessors.registerWith(blue);
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
