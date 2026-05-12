package blue.contract.processor.conversation;

import blue.contract.processor.BlueDocumentProcessors;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.ProcessorFatalException;
import blue.repo.BlueRepository;
import blue.repo.v1_2_0.conversation.ChatMessage;
import blue.repo.v1_2_0.conversation.StatusCompleted;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerEventStepExecutorTest {

    @Test
    void emitsStaticEventPayload() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowDocument(fixture.repository,
                0,
                triggerEventStep(chatMessageEvent("Hello World"))));

        DocumentProcessingResult result = processChat(fixture, document);

        assertEquals(1, result.triggeredEvents().size());
        assertEventType(result.triggeredEvents().get(0), ChatMessage.qualifiedName(), ChatMessage.blueId());
        assertEquals("Hello World", result.triggeredEvents().get(0).get("/message"));
    }

    @Test
    void resolvesNamedStepResults() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowDocument(fixture.repository,
                0,
                javaScriptStep("PreparePayment", "return { amount: 125, description: \"Subscription renewal\" };"),
                triggerEventStep(chatMessageEvent("Prepared ${steps.PreparePayment.amount} USD"))));

        DocumentProcessingResult result = processChat(fixture, document);

        assertTriggeredChatMessage(result, "Prepared 125 USD");
    }

    @Test
    void supportsDocumentAndCurrentContractBindings() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowDocument(fixture.repository,
                3,
                "Demo trigger workflow",
                triggerEventStep(chatMessageEvent("Counter ${document('/counter')} in ${currentContract.description} / ${currentContractCanonical.description.value}"))));

        DocumentProcessingResult result = processChat(fixture, document);

        assertTriggeredChatMessage(result, "Counter 3 in Demo trigger workflow / Demo trigger workflow");
    }

    @Test
    void fullExpressionsPreserveNonStringValues() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowDocument(fixture.repository,
                1,
                triggerEventStep(new Node()
                        .properties("amount", new Node().value("${document('/counter') + 1}")))));

        DocumentProcessingResult result = processChat(fixture, document);

        assertEquals(BigInteger.valueOf(2), result.triggeredEvents().get(0).get("/amount"));
    }

    @Test
    void templateExpressionsProduceStrings() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowDocument(fixture.repository,
                1,
                triggerEventStep(chatMessageEvent("Counter is ${document('/counter')}"))));

        DocumentProcessingResult result = processChat(fixture, document);

        assertTriggeredChatMessage(result, "Counter is 1");
    }

    @Test
    void nestedListsAndObjectsResolve() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowDocument(fixture.repository,
                7,
                javaScriptStep("PreparePayment", "return { amount: 125 };"),
                triggerEventStep(new Node()
                        .type("Conversation/Chat Message")
                        .properties("details", new Node()
                                .properties("values", new Node().items(
                                        new Node().value("${document('/counter')}"),
                                        new Node().value("after ${steps.PreparePayment.amount}")))))));

        DocumentProcessingResult result = processChat(fixture, document);
        Node event = result.triggeredEvents().get(0);

        assertEquals(BigInteger.valueOf(7), event.get("/details/values/0"));
        assertEquals("after 125", event.get("/details/values/1"));
    }

    @Test
    void embeddedDocumentsStayLiteral() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowDocument(fixture.repository,
                0,
                javaScriptStep("Prepare", "return { name: \"Worker\", secret: \"keep-literal\" };"),
                triggerEventStep(new Node()
                        .type("Conversation/Chat Message")
                        .properties("message", new Node().value("Launching ${steps.Prepare.name}"))
                        .properties("document", new Node()
                                .name("Child Worker Session")
                                .properties("token", new Node().value(""))
                                .properties("contracts", new Node()
                                        .properties("nestedWorkflow", new Node()
                                                .type("Conversation/Sequential Workflow")
                                                .properties("steps", new Node().items(
                                                        updateDocumentStep("replace",
                                                                "/token",
                                                                new Node().value("${steps.Prepare.secret}"))))))))));

        DocumentProcessingResult result = processChat(fixture, document);
        Node event = result.triggeredEvents().get(0);

        assertEquals("Launching Worker", event.get("/message"));
        assertEquals("${steps.Prepare.secret}",
                event.get("/document/contracts/nestedWorkflow/steps/0/changeset/0/val"));
    }

    @Test
    void missingEventFailsClearly() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowDocument(fixture.repository,
                0,
                new Node().type("Conversation/Trigger Event")));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> processChat(fixture, document));

        assertTrue(ex.getMessage().contains("Trigger Event step must declare event payload"));
    }

    @Test
    void namedEventOnlyFailsClearlyAsMissingSemanticPayload() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowDocument(fixture.repository,
                0,
                triggerEventStep(new Node().name("Named Event Only"))));

        ProcessorFatalException ex = assertThrows(ProcessorFatalException.class,
                () -> processChat(fixture, document));

        // Trigger Event requires semantic payload content such as type, value,
        // properties, items, or a blueId; name/description-only metadata is not emitted.
        assertTrue(ex.getMessage().contains("Trigger Event step must declare event payload"));
    }

    @Test
    void emittedEventIsDeliveredToRealCoreTriggeredChannel() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, triggeredConsumerDocument(fixture.repository));

        DocumentProcessingResult result = processChat(fixture, document);

        assertContainsEventType(result.triggeredEvents(), StatusCompleted.qualifiedName(), StatusCompleted.blueId());
        assertContainsChatMessage(result.triggeredEvents(), "Triggered consumer ran");
    }

    @Test
    void lifecycleProducerCanTriggerConsumer() {
        Fixture fixture = configuredFixture();

        DocumentProcessingResult result = fixture.blue.initializeDocument(
                fixture.blue.preprocess(lifecycleProducerDocument(fixture.repository)));

        assertContainsEventType(result.triggeredEvents(), StatusCompleted.qualifiedName(), StatusCompleted.blueId());
        assertContainsChatMessage(result.triggeredEvents(), "Init triggered consumer");
    }

    @Test
    void triggerEventDoesNotMutateDocumentState() {
        Fixture fixture = configuredFixture();
        Node document = initializedDocument(fixture, directWorkflowDocument(fixture.repository,
                9,
                triggerEventStep(chatMessageEvent("state is external"))));

        DocumentProcessingResult result = processChat(fixture, document);

        assertEquals(BigInteger.valueOf(9), result.document().get("/counter"));
        assertTriggeredChatMessage(result, "state is external");
    }

    private static Node directWorkflowDocument(BlueRepository repository, int counter, Node... steps) {
        return directWorkflowDocument(repository, counter, null, steps);
    }

    private static Node directWorkflowDocument(BlueRepository repository,
                                               int counter,
                                               String description,
                                               Node... steps) {
        Map<String, Node> contracts = ownerChannelContracts();
        Node workflow = new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("ownerChannel"))
                .properties("steps", new Node().items(steps));
        if (description != null) {
            workflow.description(description);
        }
        contracts.put("direct", workflow);
        return document(repository, counter, contracts);
    }

    private static Node triggeredConsumerDocument(BlueRepository repository) {
        Map<String, Node> contracts = ownerChannelContracts();
        contracts.put("triggered", new Node()
                .type("Core/Triggered Event Channel"));
        contracts.put("producer", new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("ownerChannel"))
                .properties("steps", new Node().items(
                        triggerEventStep(new Node().type("Conversation/Status Completed")))));
        contracts.put("consumer", new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("triggered"))
                .properties("event", new Node().type("Conversation/Status Completed"))
                .properties("steps", new Node().items(
                        triggerEventStep(chatMessageEvent("Triggered consumer ran")))));
        return document(repository, 0, contracts);
    }

    private static Node lifecycleProducerDocument(BlueRepository repository) {
        Map<String, Node> contracts = new LinkedHashMap<String, Node>();
        contracts.put("life", new Node()
                .type("Core/Lifecycle Event Channel"));
        contracts.put("triggered", new Node()
                .type("Core/Triggered Event Channel"));
        contracts.put("onInit", new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("life"))
                .properties("event", new Node().type("Core/Document Processing Initiated"))
                .properties("steps", new Node().items(
                        triggerEventStep(new Node().type("Conversation/Status Completed")))));
        contracts.put("consumer", new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("triggered"))
                .properties("event", new Node().type("Conversation/Status Completed"))
                .properties("steps", new Node().items(
                        triggerEventStep(chatMessageEvent("Init triggered consumer")))));
        return document(repository, 0, contracts);
    }

    private static Map<String, Node> ownerChannelContracts() {
        Map<String, Node> contracts = new LinkedHashMap<String, Node>();
        contracts.put("ownerChannel", TestTimelineProvider.channel("owner"));
        return contracts;
    }

    private static Node triggerEventStep(Node event) {
        return new Node()
                .type("Conversation/Trigger Event")
                .properties("event", event);
    }

    private static Node javaScriptStep(String name, String code) {
        return new Node()
                .name(name)
                .type("Conversation/JavaScript Code")
                .properties("code", new Node().value(code));
    }

    private static Node updateDocumentStep(String op, String path, Node value) {
        return new Node()
                .type("Conversation/Update Document")
                .properties("changeset", new Node().items(new Node()
                        .properties("op", new Node().value(op))
                        .properties("path", new Node().value(path))
                        .properties("val", value)));
    }

    private static Node chatMessageEvent(String message) {
        return new Node()
                .type("Conversation/Chat Message")
                .properties("message", new Node().value(message));
    }

    private static Node document(BlueRepository repository, int counter, Map<String, Node> contracts) {
        return new Node()
                .blue(repository.typeAliasBlue())
                .name("Trigger Event Test")
                .properties("counter", new Node().value(counter))
                .properties("contracts", new Node().properties(contracts));
    }

    private static DocumentProcessingResult processChat(Fixture fixture, Node document) {
        return fixture.blue.processDocument(document, chatTimelineEntry(fixture));
    }

    private static Node chatTimelineEntry(Fixture fixture) {
        Node event = new Node()
                .blue(fixture.repository.typeAliasBlue())
                .type("Conversation/Timeline Entry")
                .properties("timeline", new Node()
                        .properties("timelineId", new Node().value("owner")))
                .properties("timestamp", new Node().value(1))
                .properties("message", chatMessageEvent("run"));
        return fixture.blue.preprocess(event);
    }

    private static Node initializedDocument(Fixture fixture, Node document) {
        return fixture.blue.initializeDocument(fixture.blue.preprocess(document)).document();
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_2_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        BlueDocumentProcessors.registerWith(blue);
        TestTimelineProvider.registerWith(blue);
        return new Fixture(repository, blue);
    }

    private static void assertTriggeredChatMessage(DocumentProcessingResult result, String expectedMessage) {
        assertEquals(1, result.triggeredEvents().size());
        assertContainsChatMessage(result.triggeredEvents(), expectedMessage);
    }

    private static void assertContainsChatMessage(List<Node> events, String expectedMessage) {
        for (Node event : events) {
            if (isEventType(event, ChatMessage.qualifiedName(), ChatMessage.blueId())
                    && expectedMessage.equals(event.get("/message"))) {
                return;
            }
        }
        assertFalse(true, "Expected triggered chat message: " + expectedMessage);
    }

    private static void assertContainsEventType(List<Node> events, String qualifiedName, String blueId) {
        for (Node event : events) {
            if (isEventType(event, qualifiedName, blueId)) {
                return;
            }
        }
        assertFalse(true, "Expected triggered event type: " + qualifiedName);
    }

    private static void assertEventType(Node event, String qualifiedName, String blueId) {
        assertTrue(isEventType(event, qualifiedName, blueId),
                "Expected event type " + qualifiedName + " but was " + event);
    }

    private static boolean isEventType(Node event, String qualifiedName, String blueId) {
        if (event == null) {
            return false;
        }
        Node type = event.getType();
        if (type != null) {
            if (qualifiedName.equals(type.getValue())) {
                return true;
            }
            if (blueId.equals(type.getBlueId())) {
                return true;
            }
        }
        if (event.getProperties() == null) {
            return false;
        }
        Node typeProperty = event.getProperties().get("type");
        Object value = typeProperty != null ? typeProperty.getValue() : null;
        if (qualifiedName.equals(value)) {
            return true;
        }
        int slash = qualifiedName.indexOf('/');
        String localName = slash >= 0 ? qualifiedName.substring(slash + 1) : qualifiedName;
        return localName.equals(value);
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
