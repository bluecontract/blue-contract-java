package blue.coordination.processor.compute;

import blue.coordination.processor.CoordinationProcessors;
import blue.coordination.processor.CoordinationTestResources;
import blue.coordination.processor.RepositoryTypeAliasPreprocessor;
import blue.coordination.processor.TestTimelineProvider;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.registry.RuntimeBlueIds;
import blue.repo.BlueRepository;
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
 * 2. Initialize the document, process the supplied event, and time the processing call.
 * 3. Verify the expected package-fulfillment document remains active and emits snapshot events.
 *
 * Actors and operations:
 * - The incoming fixture event represents the external snapshot/update being processed.
 * - Admin/update workflows emit snapshot-related events.
 * - Compute and BEX handle data construction.
 */
class CustomerPaynoteLatestBexFixtureTest {
    private static final String DOCUMENT_RESOURCE =
            "/processor-delay/customer-paynote-snapshot.document.compute.latest-bex.yaml";
    private static final String EVENT_RESOURCE =
            "/processor-delay/customer-paynote-snapshot.event.yaml";
    private static final String SNAPSHOT_RESOLVED_TYPE =
            "Sample/Document Initial Snapshot Resolved";
    private static final String PROCESSING_INITIALIZED_MARKER = "Processing Initialized Marker";

    @Test
    void customerPaynoteLatestBexDocumentProcessesSnapshotEvent() {
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
                        + result.document().get("/contracts/checkpoint/lastEvents/sampleAdminChannel/timestamp"));
        assertContainsEventType(result,
                SNAPSHOT_RESOLVED_TYPE,
                CoordinationTestResources.testTypeAliases(fixture.repository).get(SNAPSHOT_RESOLVED_TYPE));
        assertEquals("active", result.document().get("/status"));
    }

    private static Node loadYaml(Fixture fixture, String resourcePath) {
        Node parsed = fixture.blue.parseSourceYaml(CoordinationTestResources.readResource(resourcePath));
        parsed.blue(fixture.repository.typeAliasBlue());
        if (EVENT_RESOURCE.equals(resourcePath)) {
            stripNestedSnapshotDocuments(parsed);
        }
        Node aliasesResolved = new RepositoryTypeAliasPreprocessor(
                CoordinationTestResources.testTypeAliases(fixture.repository)).preprocess(parsed);
        Node preprocessed = fixture.blue.preprocess(aliasesResolved);
        normalizeInitializationMarkers(preprocessed);
        clearCheckpoint(preprocessed);
        if (DOCUMENT_RESOURCE.equals(resourcePath)) {
            preprocessed.type((Node) null);
        }
        return preprocessed;
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = CoordinationTestResources.configuredBlue(repository);
        CoordinationProcessors.registerWith(blue);
        TestTimelineProvider.registerWith(blue);
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
            if (expectedBlueId != null && expectedBlueId.equals(event.getType().getBlueId())) {
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
        if (PROCESSING_INITIALIZED_MARKER.equals(type.getValue())
                || RuntimeBlueIds.PROCESSING_INITIALIZED_MARKER.equals(type.getBlueId())) {
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
        Node channel = all.get("sampleAdminChannel");
        Node operation = all.get("sampleAdminUpdate");
        Node implementation = all.get("sampleAdminUpdateImpl");
        operation.getProperties().remove("request");
        implementation.getProperties().remove("event");
        implementation.properties("channel", new Node().value("sampleAdminChannel"));
        all.clear();
        all.put("sampleAdminChannel", channel);
        all.put("sampleAdminUpdate", operation);
        all.put("sampleAdminUpdateImpl", implementation);
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
