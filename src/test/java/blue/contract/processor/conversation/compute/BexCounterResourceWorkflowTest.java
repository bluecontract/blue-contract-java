package blue.contract.processor.conversation.compute;

import blue.contract.processor.BlueDocumentProcessors;
import blue.contract.processor.conversation.TestTimelineProvider;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.repo.BlueRepository;
import blue.repo.conversation.OperationRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BexCounterResourceWorkflowTest {
    private static final String COUNTER_RESOURCE = "/conversation/counter-bex.yaml";
    private static final String TIMELINE_ID = "counter-timeline";

    @Test
    void counterBexWorkflowProcessesTimelineIncrementOperation() throws IOException {
        Fixture fixture = configuredFixture();
        Node document = loadYaml(fixture, COUNTER_RESOURCE);
        DocumentProcessingResult initialized = fixture.blue.initializeDocument(document);
        Node event = TestTimelineProvider.timelineEntry(fixture.blue,
                fixture.repository,
                TIMELINE_ID,
                1700000001,
                operationRequest("increment", 1));

        DocumentProcessingResult result = fixture.blue.processDocument(initialized.document(), event);

        assertFalse(result.capabilityFailure(), result.failureReason());
        assertNotNull(result.document());
        assertEquals(BigInteger.ONE, result.document().get("/counter"));
        assertEquals(1, result.triggeredEvents().size());
        assertEquals("Counter was incremented by 1 and is now 1",
                result.triggeredEvents().get(0).getAsText("/message"));
    }

    private static Node loadYaml(Fixture fixture, String resourcePath) throws IOException {
        Node node = fixture.blue.yamlToNode(readResource(resourcePath));
        node.blue(fixture.repository.typeAliasBlue());
        return fixture.blue.preprocess(node);
    }

    private static String readResource(String resourcePath) throws IOException {
        InputStream stream = BexCounterResourceWorkflowTest.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IOException("Missing test resource: " + resourcePath);
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
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

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_3_0();
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
