package blue.contract.processor.conversation;

import blue.contract.processor.BlueDocumentProcessors;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.repo.BlueRepository;
import blue.repo.v1_2_0.conversation.OperationRequest;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryStyleCounterDocumentTest {
    private static final String TIMELINE_ID = "bb13b2d9-3df9-5fea-9fdf-dd4f0ae74486";

    @Test
    void richCounterDocumentInitializesAndProcessesIncrementOperation() {
        Fixture fixture = configuredFixture();
        Node authored = richCounterDocument(fixture);

        assertNull(property(property(authored, "contracts"), "initialized"));
        assertNull(property(property(authored, "contracts"), "checkpoint"));

        DocumentProcessingResult initialized = fixture.blue.initializeDocument(authored);

        assertFalse(initialized.capabilityFailure(), initialized.failureReason());
        assertTrue(fixture.blue.isInitialized(initialized.document()));
        assertNotNull(initialized.snapshot());
        assertNotNull(initialized.blueId());
        String initializedDocumentId = initialized.resolvedDocument().getAsText("/contracts/initialized/documentId");
        assertNotNull(initializedDocumentId);
        assertNull(property(property(initialized.resolvedDocument(), "contracts"), "checkpoint"));

        Node event = TestTimelineProvider.timelineEntry(fixture.blue,
                fixture.repository,
                TIMELINE_ID,
                1777987926,
                operationRequest("increment", 5));

        DocumentProcessingResult result = fixture.blue.processDocument(initialized.snapshot(), event);

        assertFalse(result.capabilityFailure(), result.failureReason());
        assertNotNull(result.snapshot());
        assertNotNull(result.blueId());
        assertEquals(BigInteger.valueOf(5), result.resolvedDocument().get("/counter"));
        assertEquals(1, result.triggeredEvents().size());
        assertEquals("Counter was incremented by 5 and is now 5",
                result.triggeredEvents().get(0).getAsText("/message"));

        Node resolved = result.resolvedDocument();
        assertEquals(initializedDocumentId, resolved.getAsText("/contracts/initialized/documentId"));
        assertEquals(TIMELINE_ID, resolved.getAsText("/contracts/checkpoint/lastEvents/ownerChannel/timeline/timelineId"));
        assertEquals(BigInteger.valueOf(1777987926L),
                resolved.get("/contracts/checkpoint/lastEvents/ownerChannel/timestamp"));
        assertEquals("increment",
                resolved.getAsText("/contracts/checkpoint/lastEvents/ownerChannel/message/operation"));
        assertEquals(BigInteger.valueOf(5),
                resolved.get("/contracts/checkpoint/lastEvents/ownerChannel/message/request"));
        Object signature = resolved.get("/contracts/checkpoint/lastSignatures/ownerChannel");
        assertTrue(signature instanceof String);
        assertFalse(((String) signature).isEmpty());
    }

    private static Node richCounterDocument(Fixture fixture) {
        Node parsed = fixture.blue.yamlToNode(richCounterDocumentYaml());
        return fixture.blue.preprocess(parsed.blue(fixture.repository.typeAliasBlue()));
    }

    private static String richCounterDocumentYaml() {
        return String.join("\n",
                "name: Counter - 2026-04-21T09:47:18.314Z",
                "description: Target Blue document to be bootstrapped",
                "counter: 0",
                "contracts:",
                "  ownerChannel:",
                "    type:",
                "      blueId: " + TestTimelineProvider.SIMPLE_TIMELINE_CHANNEL_BLUE_ID,
                "    order:",
                "      description: Deterministic sort key within a scope; missing == 0.",
                "      type: Integer",
                "    event:",
                "      description: Optional matcher payload used by the channel's processor to further restrict which incoming events it accepts at this scope.",
                "    timelineId:",
                "      description: The `timelineId` whose entries this channel delivers.",
                "      type: Text",
                "      value: " + TIMELINE_ID,
                "  increment:",
                "    description: Increment the counter by the given number",
                "    type: Conversation/Operation",
                "    order:",
                "      description: Deterministic sort key within a scope; missing == 0.",
                "      type: Integer",
                "    channel:",
                "      description: Contracts-map key of the Channel in this scope on which Operation Request events are sent to invoke this operation.",
                "      type: Text",
                "      value: ownerChannel",
                "    request:",
                "      description: Represents a value by which counter will be incremented",
                "      type: Integer",
                "  incrementImpl:",
                "    type: Conversation/Sequential Workflow Operation",
                "    order:",
                "      description: Deterministic sort key within a scope; missing == 0.",
                "      type: Integer",
                "    channel:",
                "      description: The contracts-map key of the channel this handler binds to (same scope).",
                "      type: Text",
                "    event:",
                "      description: Optional matcher payload used by the handler's processor to further restrict events.",
                "    steps:",
                "      description: Ordered list of steps to execute (positional semantics).",
                "      type: List",
                "      itemType: Conversation/Sequential Workflow Step",
                "      items:",
                "        - type: Conversation/Update Document",
                "          changeset:",
                "            - op: replace",
                "              path: /counter",
                "              val: ${event.message.request + document('/counter')}",
                "        - name: CreateMessageEvent",
                "          type: Conversation/JavaScript Code",
                "          code:",
                "            description: JavaScript source to execute for this step.",
                "            type: Text",
                "            value: |-",
                "              const message = `Counter was incremented by ${event.message.request} and is now ${document('/counter')}`;",
                "",
                "              return {",
                "                events: [",
                "                  {",
                "                    type: \"Conversation/Chat Message\",",
                "                    message: message,",
                "                  },",
                "                ],",
                "              };",
                "    operation:",
                "      description: The name of the Operation this handler implements. Must reference an Operation defined in the same scope.",
                "      type: Text",
                "      value: increment",
                "  decrement:",
                "    description: Decrement the counter by the given number",
                "    type: Conversation/Operation",
                "    order:",
                "      description: Deterministic sort key within a scope; missing == 0.",
                "      type: Integer",
                "    channel:",
                "      description: Contracts-map key of the Channel in this scope on which Operation Request events are sent to invoke this operation.",
                "      type: Text",
                "      value: ownerChannel",
                "    request:",
                "      description: Value to subtract",
                "      type: Integer",
                "  decrementImpl:",
                "    type: Conversation/Sequential Workflow Operation",
                "    order:",
                "      description: Deterministic sort key within a scope; missing == 0.",
                "      type: Integer",
                "    channel:",
                "      description: The contracts-map key of the channel this handler binds to (same scope).",
                "      type: Text",
                "    event:",
                "      description: Optional matcher payload used by the handler's processor to further restrict events.",
                "    steps:",
                "      description: Ordered list of steps to execute (positional semantics).",
                "      type: List",
                "      itemType: Conversation/Sequential Workflow Step",
                "      items:",
                "        - type: Conversation/Update Document",
                "          changeset:",
                "            - op: replace",
                "              path: /counter",
                "              val: ${document('/counter') - event.message.request}",
                "        - name: CreateMessageEvent",
                "          type: Conversation/JavaScript Code",
                "          code:",
                "            description: JavaScript source to execute for this step.",
                "            type: Text",
                "            value: |-",
                "              const message = `Counter was decremented by ${event.message.request} and is now ${document('/counter')}`;",
                "",
                "              return {",
                "                events: [",
                "                  {",
                "                    type: \"Conversation/Chat Message\",",
                "                    message: message,",
                "                  },",
                "                ],",
                "              };",
                "    operation:",
                "      description: The name of the Operation this handler implements. Must reference an Operation defined in the same scope.",
                "      type: Text",
                "      value: decrement");
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

    private static Node property(Node node, String key) {
        if (node == null || node.getProperties() == null) {
            return null;
        }
        return node.getProperties().get(key);
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_2_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
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
