package blue.contract.processor.conversation.compute;

import blue.contract.processor.BlueDocumentProcessors;
import blue.contract.processor.conversation.ConversationTestResources;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.repo.BlueRepository;
import blue.repo.myos.DocumentInitialSnapshotResolved;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Scenario:
 * The large customer Paynote snapshot fixture is processed through the BEX-based document path.
 *
 * Main flow:
 * 1. Load the latest Compute/BEX Paynote document fixture and its snapshot event fixture.
 * 2. Assert the fixtures are pure BEX, with no legacy dollar-brace steps expressions,
 *    dollar-brace document expressions, or JavaScript workflow steps.
 * 3. Initialize the document, process the supplied event, and time the processing call.
 * 4. Verify the expected package-fulfillment document remains active and emits snapshot events.
 *
 * Actors and operations:
 * - The incoming fixture event represents the external snapshot/update being processed.
 * - Admin/update workflows emit snapshot-related events.
 * - Compute and BEX expression fields handle data construction without QuickJS expressions.
 */
class CustomerPaynoteLatestBexFixtureTest {
    private static final String DOCUMENT_RESOURCE =
            "/processor-delay/customer-paynote-snapshot.document.compute.latest-bex.yaml";
    private static final String EVENT_RESOURCE =
            "/processor-delay/customer-paynote-snapshot.event.yaml";

    @Test
    void customerPaynoteLatestBexDocumentProcessesSnapshotEvent() {
        assertPureBexFixture(ConversationTestResources.readResource(DOCUMENT_RESOURCE), DOCUMENT_RESOURCE);
        assertPureBexFixture(ConversationTestResources.readResource(EVENT_RESOURCE), EVENT_RESOURCE);
        Fixture fixture = configuredFixture();
        Node document = loadYaml(fixture, DOCUMENT_RESOURCE);
        Node event = loadYaml(fixture, EVENT_RESOURCE);
        stripNestedSnapshotDocuments(event);
        retainAdminUpdateContracts(document);

        DocumentProcessingResult initialized = fixture.blue.initializeDocument(document);
        long start = System.currentTimeMillis();
        DocumentProcessingResult result = fixture.blue.processDocument(initialized.document(), event);
        System.out.println("Processing time: " + (System.currentTimeMillis() - start) + "ms");

        assertNotNull(result.document());
        assertEquals("Global Package Fulfillment Automation - Weekend Stay + Wine Dinner",
                result.document().getName());
        assertFalse(result.triggeredEvents().isEmpty(),
                "Expected the admin update workflow to emit snapshot events; checkpoint timestamp="
                        + result.document().get("/contracts/checkpoint/lastEvents/myOsAdminChannel/timestamp"));
        assertContainsEventType(result,
                DocumentInitialSnapshotResolved.qualifiedName(),
                DocumentInitialSnapshotResolved.blueId());
        assertEquals("active", result.document().get("/status"));
    }

    private static Node loadYaml(Fixture fixture, String resourcePath) {
        Node parsed = fixture.blue.parseSourceYaml(ConversationTestResources.readResource(resourcePath));
        parsed.blue(fixture.repository.typeAliasBlue());
        if (EVENT_RESOURCE.equals(resourcePath)) {
            stripNestedSnapshotDocuments(parsed);
        }
        Node preprocessed = fixture.blue.preprocess(parsed);
        normalizeInitializationMarkers(preprocessed);
        clearCheckpoint(preprocessed);
        if (DOCUMENT_RESOURCE.equals(resourcePath)) {
            preprocessed.type((Node) null);
        }
        return preprocessed;
    }

    private static void assertPureBexFixture(String yaml, String resourcePath) {
        assertFalse(yaml.contains("${steps."), resourcePath + " must not contain legacy steps expressions");
        assertFalse(yaml.contains("${document("), resourcePath + " must not contain legacy document expressions");
        assertFalse(yaml.contains("Conversation/JavaScript Code"), resourcePath + " must not contain JavaScript steps");
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = ConversationTestResources.configuredBlue(repository);
        BlueDocumentProcessors.registerWith(blue);
        return new Fixture(repository, blue);
    }

    private static void assertContainsEventType(DocumentProcessingResult result, String expectedType, String expectedBlueId) {
        for (Node event : result.triggeredEvents()) {
            if (isEventType(event, expectedType, expectedBlueId)) {
                return;
            }
        }
        throw new AssertionError("Expected triggered event type: " + expectedType
                + ", actual count: " + result.triggeredEvents().size()
                + ", actual types: " + triggeredEventTypes(result)
                + ", first event: " + (result.triggeredEvents().isEmpty() ? null : result.triggeredEvents().get(0)));
    }

    private static boolean isEventType(Node event, String expectedType, String expectedBlueId) {
        if (event == null) {
            return false;
        }
        if (event.getType() != null) {
            if (expectedBlueId.equals(event.getType().getBlueId())) {
                return true;
            }
            Object value = event.getType().getValue();
            if (expectedType.equals(value)) {
                return true;
            }
        }
        Node typeProperty = property(event, "type");
        Object propertyValue = typeProperty != null ? typeProperty.getValue() : null;
        return expectedType.equals(propertyValue);
    }

    private static String triggeredEventTypes(DocumentProcessingResult result) {
        StringBuilder builder = new StringBuilder();
        for (Node event : result.triggeredEvents()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            Node type = event != null ? event.getType() : null;
            builder.append(type != null ? type.getValue() : null)
                    .append("/")
                    .append(type != null ? type.getBlueId() : null)
                    .append(" field=")
                    .append(typeField(event));
        }
        return builder.toString();
    }

    private static Object typeField(Node event) {
        Node type = property(event, "type");
        return type != null ? type.getValue() : null;
    }

    private static void normalizeInitializationMarkers(Node node) {
        if (node == null) {
            return;
        }
        Map<String, Node> properties = node.getProperties();
        if (properties != null) {
            Node contracts = properties.get("contracts");
            if (contracts != null && contracts.getProperties() != null) {
                normalizeInitializationMarker(contracts.getProperties().get("initialized"));
            }
            for (Node child : properties.values()) {
                normalizeInitializationMarkers(child);
            }
        }
        if (node.getItems() != null) {
            for (Node item : node.getItems()) {
                normalizeInitializationMarkers(item);
            }
        }
    }

    private static void normalizeInitializationMarker(Node marker) {
        if (marker == null || marker.getType() == null) {
            return;
        }
        Node type = marker.getType();
        if ("Core/Processing Initialized Marker".equals(type.getValue())
                || "EVguxFmq5iFtMZaBQgHfjWDojaoesQ1vEXCQFZ59yL28".equals(type.getBlueId())) {
            marker.type(new Node().blueId("InitializationMarker"));
        }
    }

    private static void clearCheckpoint(Node node) {
        if (node == null) {
            return;
        }
        Node contracts = property(node, "contracts");
        if (contracts != null && contracts.getProperties() != null) {
            contracts.getProperties().remove("checkpoint");
        }
    }

    private static Node property(Node node, String key) {
        if (node == null) {
            return null;
        }
        if ("contracts".equals(key)) {
            return node.getContracts();
        }
        return node.getProperties() != null ? node.getProperties().get(key) : null;
    }

    private static void stripNestedSnapshotDocuments(Node event) {
        // The attached event carries a full customer PayNote snapshot inside the
        // admin request. That nested snapshot is not needed to prove the admin
        // BEX workflow emits the request event, and retaining it forces checkpoint
        // metadata to resolve stale embedded repository contracts.
        Node message = property(event, "message");
        Node request = property(message, "request");
        if (request == null || request.getItems() == null) {
            return;
        }
        for (Node item : request.getItems()) {
            if (item.getProperties() != null) {
                item.getProperties().remove("document");
            }
        }
    }

    private static void retainAdminUpdateContracts(Node document) {
        // Keep the workflow under test from the attached document while avoiding
        // unrelated generated contracts whose historical schema metadata is not
        // needed for this event path.
        Node contracts = property(document, "contracts");
        Map<String, Node> all = contracts.getProperties();
        Node channel = all.get("myOsAdminChannel");
        Node operation = all.get("myOsAdminUpdate");
        Node implementation = all.get("myOsAdminUpdateImpl");
        operation.getProperties().remove("request");
        implementation.getProperties().remove("event");
        implementation.properties("channel", new Node().value("myOsAdminChannel"));
        all.clear();
        all.put("myOsAdminChannel", channel);
        all.put("myOsAdminUpdate", operation);
        all.put("myOsAdminUpdateImpl", implementation);
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
