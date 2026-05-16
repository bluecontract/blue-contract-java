package blue.contract.processor;

import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.ContractProcessorRegistry;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.DocumentProcessor;
import blue.language.processor.model.ChannelContract;
import blue.language.processor.model.HandlerContract;
import blue.language.processor.model.MarkerContract;
import blue.language.utils.TypeClassResolver;
import blue.repo.BlueRepository;
import blue.repo.v1_3_0.conversation.ChatMessage;
import blue.repo.v1_3_0.conversation.CompositeTimelineChannel;
import blue.repo.v1_3_0.conversation.JavaScriptCode;
import blue.repo.v1_3_0.conversation.Operation;
import blue.repo.v1_3_0.conversation.OperationRequest;
import blue.repo.v1_3_0.conversation.SequentialWorkflow;
import blue.repo.v1_3_0.conversation.SequentialWorkflowOperation;
import blue.repo.v1_3_0.conversation.TimelineChannel;
import blue.repo.v1_3_0.conversation.UpdateDocument;
import blue.repo.v1_3_0.myos.MyOSTimelineChannel;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueDocumentProcessorsTest {

    @Test
    void registerWithBlueRegistersConversationProcessors() {
        Fixture fixture = configuredFixture();

        assertConversationProcessorsRegistered(fixture.blue.getDocumentProcessor());
    }

    @Test
    void configureBuilderRegistersConversationProcessors() {
        DocumentProcessor processor =
                BlueDocumentProcessors.configure(DocumentProcessor.builder()).build();

        assertConversationProcessorsRegistered(processor);
    }

    @Test
    void realRepositoryConversationContractsLoadAndInitialize() {
        Fixture fixture = configuredFixture();
        Node document = counterDocument(fixture.repository, "ownerChannel");
        Node preprocessed = fixture.blue.preprocess(document.clone());
        Map<String, Node> contracts = contracts(preprocessed);

        assertEquals(MyOSTimelineChannel.blueId(), contracts.get("ownerChannel").getType().getBlueId());
        assertEquals(Operation.blueId(), contracts.get("increment").getType().getBlueId());
        assertEquals(SequentialWorkflowOperation.blueId(),
                contracts.get("incrementImpl").getType().getBlueId());

        Object convertedOperation = fixture.blue.nodeToObject(contracts.get("increment"), Object.class);
        assertTrue(convertedOperation instanceof Operation);
        assertEquals("ownerChannel", ((Operation) convertedOperation).getChannel());

        Object convertedHandler = fixture.blue.nodeToObject(contracts.get("incrementImpl"), Object.class);
        assertTrue(convertedHandler instanceof SequentialWorkflowOperation);
        SequentialWorkflowOperation handler = (SequentialWorkflowOperation) convertedHandler;
        assertEquals("increment", handler.getOperation());
        assertNotNull(handler.getSteps());
        assertTrue(handler.getSteps().isEmpty());

        DocumentProcessingResult result = fixture.blue.initializeDocument(preprocessed);

        assertFalse(result.capabilityFailure(), result.failureReason());
        assertTrue(fixture.blue.isInitialized(result.document()));
        assertEquals(BigInteger.ZERO, result.document().getProperties().get("counter").getValue());
        assertFalse(contracts(result.document()).containsKey("checkpoint"));
    }

    @Test
    void initializationFailsWhenSequentialWorkflowOperationDerivesMissingChannel() {
        Fixture fixture = configuredFixture();
        Node document = counterDocument(fixture.repository, "missingChannel");
        Node preprocessed = fixture.blue.preprocess(document.clone());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fixture.blue.initializeDocument(preprocessed));

        assertTrue(ex.getMessage().contains("unknown channel"));
        assertTrue(ex.getMessage().contains("missingChannel"));
    }

    @Test
    void generatedRepositoryContractsProvideProcessorModelBaseTypes() {
        assertTrue(ChannelContract.class.isAssignableFrom(TimelineChannel.class));
        assertTrue(ChannelContract.class.isAssignableFrom(CompositeTimelineChannel.class));
        assertTrue(HandlerContract.class.isAssignableFrom(SequentialWorkflow.class));
        assertTrue(HandlerContract.class.isAssignableFrom(SequentialWorkflowOperation.class));
        assertTrue(MarkerContract.class.isAssignableFrom(Operation.class));
    }

    @Test
    void generatedConversationTypesResolveToRepositoryClasses() {
        TypeClassResolver resolver = BlueRepository.v1_3_0().typeClassResolver();

        assertEquals(TimelineChannel.class, resolver.resolveClass(TimelineChannel.blueId()));
        assertEquals(CompositeTimelineChannel.class,
                resolver.resolveClass(CompositeTimelineChannel.blueId()));
        assertEquals(MyOSTimelineChannel.class, resolver.resolveClass(MyOSTimelineChannel.blueId()));
        assertEquals(Operation.class, resolver.resolveClass(Operation.blueId()));
        assertEquals(SequentialWorkflow.class, resolver.resolveClass(SequentialWorkflow.blueId()));
        assertEquals(SequentialWorkflowOperation.class,
                resolver.resolveClass(SequentialWorkflowOperation.blueId()));
        assertEquals(UpdateDocument.class, resolver.resolveClass(UpdateDocument.blueId()));
        assertEquals(JavaScriptCode.class, resolver.resolveClass(JavaScriptCode.blueId()));
        assertEquals(ChatMessage.class, resolver.resolveClass(ChatMessage.blueId()));
        assertEquals(OperationRequest.class, resolver.resolveClass(OperationRequest.blueId()));
    }

    private static void assertConversationProcessorsRegistered(DocumentProcessor processor) {
        ContractProcessorRegistry registry = processor.getContractRegistry();

        assertFalse(registry.lookupChannel(TimelineChannel.blueId()).isPresent());
        assertTrue(registry.lookupChannel(MyOSTimelineChannel.blueId()).isPresent());
        assertTrue(registry.lookupChannel(CompositeTimelineChannel.blueId()).isPresent());
        assertTrue(registry.lookupMarker(Operation.blueId()).isPresent());
        assertTrue(registry.lookupHandler(SequentialWorkflow.blueId()).isPresent());
        assertTrue(registry.lookupHandler(SequentialWorkflowOperation.blueId()).isPresent());
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        BlueDocumentProcessors.registerWith(blue);
        return new Fixture(repository, blue);
    }

    private static Node counterDocument(BlueRepository repository, String operationChannel) {
        Map<String, Node> contracts = new LinkedHashMap<>();
        contracts.put("ownerChannel", new Node()
                .type("MyOS/MyOS Timeline Channel")
                .properties("timelineId", new Node().value("owner"))
                .properties("accountId", new Node().value("account")));
        contracts.put("increment", new Node()
                .type("Conversation/Operation")
                .properties("channel", new Node().value(operationChannel))
                .properties("request", new Node().type("Integer")));
        contracts.put("incrementImpl", new Node()
                .type("Conversation/Sequential Workflow Operation")
                .properties("operation", new Node().value("increment"))
                .properties("steps", new Node().items(Collections.<Node>emptyList())));

        return new Node()
                .blue(repository.typeAliasBlue())
                .name("Counter")
                .properties("counter", new Node().value(0))
                .properties("contracts", new Node().properties(contracts));
    }

    private static Map<String, Node> contracts(Node document) {
        return document.getProperties().get("contracts").getProperties();
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
