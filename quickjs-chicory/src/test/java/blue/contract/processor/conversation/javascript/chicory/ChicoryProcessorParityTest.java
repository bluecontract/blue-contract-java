package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.BlueDocumentProcessorOptions;
import blue.contract.processor.BlueDocumentProcessors;
import blue.contract.processor.conversation.TimelineProviderSupport;
import blue.contract.processor.conversation.expression.QuickJsExpressionEvaluator;
import blue.contract.processor.conversation.expression.QuickJsExpressionResolver;
import blue.contract.processor.conversation.javascript.JavaScriptExecutionException;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.javascript.NodeQuickJsRuntime;
import blue.contract.processor.conversation.javascript.QuickJsGas;
import blue.contract.processor.conversation.workflow.JavaScriptCodeStepExecutor;
import blue.contract.processor.conversation.workflow.SequentialWorkflowRunner;
import blue.contract.processor.conversation.workflow.TriggerEventStepExecutor;
import blue.contract.processor.conversation.workflow.UpdateDocumentStepExecutor;
import blue.contract.processor.conversation.workflow.WorkflowStepExecutor;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.model.TypeBlueId;
import blue.language.processor.ChannelCheckpointContext;
import blue.language.processor.ChannelEvaluation;
import blue.language.processor.ChannelEvaluationContext;
import blue.language.processor.ChannelProcessor;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.ProcessorFatalException;
import blue.repo.BlueRepository;
import blue.repo.v1_3_0.conversation.ChatMessage;
import blue.repo.v1_3_0.conversation.JavaScriptCode;
import blue.repo.v1_3_0.conversation.SequentialWorkflowStep;
import blue.repo.v1_3_0.conversation.Timeline;
import blue.repo.v1_3_0.conversation.TimelineChannel;
import blue.repo.v1_3_0.conversation.TimelineEntry;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChicoryProcessorParityTest {
    private static final String SIMPLE_TIMELINE_CHANNEL_BLUE_ID = "chicory-processor-parity-timeline-channel";

    @Test
    void processorSuccessFixturesMatchNodeOutputAndGas() {
        Path root = blueQuickJsRoot();
        ChicoryBlueQuickJsRuntime chicory = new ChicoryBlueQuickJsRuntime(ChicoryTestSupport.pinnedConfig(root));
        try (NodeQuickJsRuntime node = new NodeQuickJsRuntime(root)) {
            List<ProcessorFixture> fixtures = Arrays.asList(
                    new ProcessorFixture("counter increment with JS expression",
                            workflowDocument(0,
                                    updateDocumentStep("replace", "/counter", new Node().value("${document('/counter') + 1}")))),
                    new ProcessorFixture("workflow with JavaScript Code step emitting event",
                            workflowDocument(3,
                                    javaScriptStep("Emit", "return { events: [{ type: 'Conversation/Chat Message', message: `Counter ${document('/counter')}` }] };"))),
                    new ProcessorFixture("Trigger Event with template expressions",
                            workflowDocument(5,
                                    triggerEventStep(chatMessageEvent("Counter is ${document('/counter')}")))));

            for (ProcessorFixture fixture : fixtures) {
                ProcessorRun nodeRun = process(node, fixture);
                ProcessorRun chicoryRun = process(chicory, fixture);
                assertEquals(nodeRun, chicoryRun, fixture.name);
            }
        }
    }

    @Test
    void processorFailureFixturesMatchNodeFatalBehavior() {
        Path root = blueQuickJsRoot();
        ChicoryBlueQuickJsRuntime chicory = new ChicoryBlueQuickJsRuntime(ChicoryTestSupport.pinnedConfig(root));
        try (NodeQuickJsRuntime node = new NodeQuickJsRuntime(root)) {
            assertFatalParity(node,
                    chicory,
                    new ProcessorFixture("JS Code throw new Error",
                            workflowDocument(0, javaScriptStep("Throw", "throw new Error('boom');"))),
                    true);
            assertFatalParity(node,
                    chicory,
                    new ProcessorFixture("JS Code out-of-gas",
                            workflowDocument(0, javaScriptStep("Loop", "while (true) {}")),
                            null,
                            1L),
                    true);
            assertFatalParity(node,
                    chicory,
                    new ProcessorFixture("Update Document expression throwing",
                            workflowDocument(0, updateDocumentStep("replace",
                                    "/counter",
                                    new Node().value("${(() => { throw new Error('boom'); })()}")))),
                    true);
            assertFatalParity(node,
                    chicory,
                    new ProcessorFixture("Update Document expression out-of-gas",
                            workflowDocument(0, updateDocumentStep("replace",
                                    "/counter",
                                    new Node().value("${(() => { while (true) {} })()}"))),
                            1L,
                            null),
                    true);
            assertFatalParity(node,
                    chicory,
                    new ProcessorFixture("Trigger Event template expression throwing",
                            workflowDocument(0, triggerEventStep(
                                    chatMessageEvent("Counter ${JSON.parse('x')}")))),
                    true);
            assertFatalParity(node,
                    chicory,
                    new ProcessorFixture("deterministic forbidden global failure",
                            workflowDocument(0, updateDocumentStep("replace",
                                    "/counter",
                                    new Node().value("${Math.random()}")))),
                    true);
            assertFatalParity(node,
                    chicory,
                    new ProcessorFixture("malformed host call failure",
                            workflowDocument(0, updateDocumentStep("replace",
                                    "/counter",
                                    new Node().value("${document(null)}")))),
                    true);
        }
    }

    @Test
    void bridgeFailureWithoutVmGasDoesNotFabricateGas() {
        ProcessorFixture fixture = new ProcessorFixture("bridge setup failure without VM execution",
                workflowDocument(0, updateDocumentStep("replace", "/counter", new Node().value("${1 + 2}"))));

        ProcessorFailure noGas = processFailure(new JavaScriptRuntime() {
            @Override
            public blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult evaluate(
                    blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest request) {
                throw new JavaScriptExecutionException("bridge setup failed before VM execution");
            }
        }, fixture);
        ProcessorFailure withGas = processFailure(new JavaScriptRuntime() {
            @Override
            public blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult evaluate(
                    blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest request) {
                throw new JavaScriptExecutionException("VM execution failed", 11L, 37L);
            }
        }, fixture);

        assertEquals(noGas.totalGas + 37L, withGas.totalGas);
        assertTrue(noGas.normalizedMessage.contains("bridge setup failed before VM execution"));
    }

    private static void assertFatalParity(JavaScriptRuntime node,
                                          JavaScriptRuntime chicory,
                                          ProcessorFixture fixture,
                                          boolean expectUnchangedDocument) {
        ProcessorFailure nodeFailure = processFailure(node, fixture);
        ProcessorFailure chicoryFailure = processFailure(chicory, fixture);

        assertEquals(nodeFailure.classification, chicoryFailure.classification, fixture.name);
        assertEquals(nodeFailure.normalizedMessage, chicoryFailure.normalizedMessage, fixture.name);
        assertEquals(nodeFailure.totalGas, chicoryFailure.totalGas, fixture.name);
        assertTrue(nodeFailure.totalGas > 0L, fixture.name + " should charge VM failure gas");
        assertEquals(nodeFailure.partialCanonicalJson, chicoryFailure.partialCanonicalJson, fixture.name);
        assertEquals(nodeFailure.partialBlueId, chicoryFailure.partialBlueId, fixture.name);
        assertEquals(nodeFailure.events, chicoryFailure.events, fixture.name);
        assertTrue(nodeFailure.events.isEmpty(), fixture.name + " should not emit events after fatal failure");
        if (expectUnchangedDocument) {
            assertEquals(nodeFailure.initialCounter, nodeFailure.partialCounter, fixture.name);
        }
    }

    private static ProcessorRun process(JavaScriptRuntime runtime, ProcessorFixture fixture) {
        Fixture configured = configuredFixture(runtime, fixture);
        DocumentProcessingResult initialized = configured.blue.initializeDocument(
                configured.blue.preprocess(fixture.document));

        DocumentProcessingResult result = configured.blue.processDocument(initialized.document(),
                timelineEntry(configured.blue, configured.repository, "owner", 1, chatMessage("run")));

        return new ProcessorRun(
                canonicalJson(configured, result),
                result.blueId(),
                eventJson(configured, result.triggeredEvents()),
                result.totalGas());
    }

    private static ProcessorFailure processFailure(JavaScriptRuntime runtime, ProcessorFixture fixture) {
        Fixture configured = configuredFixture(runtime, fixture);
        DocumentProcessingResult initialized = configured.blue.initializeDocument(
                configured.blue.preprocess(fixture.document));

        ProcessorFatalException failure = assertThrows(ProcessorFatalException.class,
                () -> configured.blue.processDocument(initialized.document(),
                        timelineEntry(configured.blue, configured.repository, "owner", 1, chatMessage("run"))));
        DocumentProcessingResult partial = failure.partialResult();
        assertNotNull(partial, fixture.name + " should expose a partial fatal result");

        return new ProcessorFailure(
                failure.getClass().getName(),
                normalize(failure.getMessage()),
                failure.totalGas(),
                canonicalJson(configured, initialized),
                initialized.blueId(),
                initialized.document().get("/counter"),
                canonicalJson(configured, partial),
                partial.blueId(),
                partial.document().get("/counter"),
                eventJson(configured, partial.triggeredEvents()));
    }

    private static Fixture configuredFixture(JavaScriptRuntime runtime, ProcessorFixture fixture) {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        BlueDocumentProcessorOptions.Builder options = BlueDocumentProcessorOptions.builder()
                .sequentialWorkflowRunner(workflowRunner(runtime, fixture));
        BlueDocumentProcessors.registerWith(blue, options.build());
        blue.registerContractProcessor(new SimpleTimelineChannelProcessor());
        return new Fixture(repository, blue);
    }

    private static SequentialWorkflowRunner workflowRunner(JavaScriptRuntime runtime, ProcessorFixture fixture) {
        long expressionHostGasLimit = fixture.expressionHostGasLimit != null
                ? fixture.expressionHostGasLimit.longValue()
                : QuickJsGas.DEFAULT_EXPRESSION_HOST_GAS_LIMIT;
        long codeHostGasLimit = fixture.codeHostGasLimit != null
                ? fixture.codeHostGasLimit.longValue()
                : QuickJsGas.DEFAULT_CODE_HOST_GAS_LIMIT;
        QuickJsExpressionResolver resolver = new QuickJsExpressionResolver(runtime, expressionHostGasLimit);
        return new SequentialWorkflowRunner(Arrays.<WorkflowStepExecutor<? extends SequentialWorkflowStep>>asList(
                new TriggerEventStepExecutor(resolver),
                new JavaScriptCodeStepExecutor(runtime, codeHostGasLimit),
                new UpdateDocumentStepExecutor(new QuickJsExpressionEvaluator(runtime, expressionHostGasLimit))));
    }

    private static Node workflowDocument(int counter, Node... steps) {
        Map<String, Node> contracts = new LinkedHashMap<String, Node>();
        contracts.put("owner", channel("owner"));
        contracts.put("direct", new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("owner"))
                .properties("steps", new Node().items(Arrays.asList(steps))));

        return new Node()
                .blue(BlueRepository.v1_3_0().typeAliasBlue())
                .name("Processor Parity Document")
                .properties("counter", new Node().value(counter))
                .properties("contracts", new Node().properties(contracts));
    }

    private static Node updateDocumentStep(String op, String path, Node value) {
        return new Node()
                .type("Conversation/Update Document")
                .properties("changeset", new Node().items(new Node()
                        .properties("op", new Node().value(op))
                        .properties("path", new Node().value(path))
                        .properties("val", value)));
    }

    private static Node javaScriptStep(String name, String code) {
        return new Node()
                .name(name)
                .type("Conversation/JavaScript Code")
                .properties("code", new Node().value(code));
    }

    private static Node triggerEventStep(Node event) {
        return new Node()
                .type("Conversation/Trigger Event")
                .properties("event", event);
    }

    private static Node chatMessageEvent(String message) {
        return new Node()
                .type(ChatMessage.qualifiedName())
                .properties("message", new Node().value(message));
    }

    private static Node channel(String timelineId) {
        return new Node()
                .type(new Node().blueId(SIMPLE_TIMELINE_CHANNEL_BLUE_ID))
                .properties("timelineId", new Node().value(timelineId));
    }

    private static Node timelineEntry(Blue blue,
                                      BlueRepository repository,
                                      String timelineId,
                                      int timestamp,
                                      Node message) {
        TimelineEntry entry = new TimelineEntry()
                .timeline(new Timeline().timelineId(timelineId))
                .timestamp(BigInteger.valueOf(timestamp))
                .message(message);
        return blue.preprocess(new Node()
                .blue(repository.typeAliasBlue())
                .type(TimelineEntry.qualifiedName())
                .properties("timeline", blue.objectToNode(entry.getTimeline()))
                .properties("timestamp", new Node().value(entry.getTimestamp()))
                .properties("message", entry.getMessage()));
    }

    private static Node chatMessage(String message) {
        return new Node()
                .type(ChatMessage.qualifiedName())
                .properties("message", new Node().value(message));
    }

    private static List<String> eventJson(Fixture fixture, List<Node> events) {
        List<String> json = new ArrayList<String>();
        for (Node event : events) {
            json.add(fixture.blue.nodeToJson(event));
        }
        return json;
    }

    private static String canonicalJson(Fixture fixture, DocumentProcessingResult result) {
        Node canonical = result.canonicalDocument();
        return fixture.blue.nodeToJson(canonical != null ? canonical : result.document());
    }

    private static String normalize(String message) {
        return message == null ? "" : message.replace("Chicory blue-quickjs evaluation failed: ", "");
    }

    private static Path blueQuickJsRoot() {
        return ChicoryTestSupport.blueQuickJsRoot("blue-quickjs checkout is required for processor parity tests");
    }

    @TypeBlueId(SIMPLE_TIMELINE_CHANNEL_BLUE_ID)
    public static final class SimpleTimelineChannel extends TimelineChannel {
    }

    public static final class SimpleTimelineChannelProcessor implements ChannelProcessor<SimpleTimelineChannel> {
        @Override
        public Class<SimpleTimelineChannel> contractType() {
            return SimpleTimelineChannel.class;
        }

        @Override
        public ChannelEvaluation evaluate(SimpleTimelineChannel contract, ChannelEvaluationContext context) {
            return TimelineProviderSupport.evaluateTimelineEntry(contract, context);
        }

        @Override
        public String eventId(SimpleTimelineChannel contract, ChannelEvaluationContext context) {
            return TimelineProviderSupport.eventId(context.event());
        }

        @Override
        public boolean isNewerEvent(SimpleTimelineChannel contract, ChannelCheckpointContext context) {
            return TimelineProviderSupport.isNewerOrSameTimelineEvent(context);
        }
    }

    private static final class ProcessorFixture {
        private final String name;
        private final Node document;
        private final Long expressionHostGasLimit;
        private final Long codeHostGasLimit;

        private ProcessorFixture(String name, Node document) {
            this(name, document, null, null);
        }

        private ProcessorFixture(String name, Node document, Long expressionHostGasLimit, Long codeHostGasLimit) {
            this.name = name;
            this.document = document;
            this.expressionHostGasLimit = expressionHostGasLimit;
            this.codeHostGasLimit = codeHostGasLimit;
        }
    }

    private static final class ProcessorFailure {
        private final String classification;
        private final String normalizedMessage;
        private final long totalGas;
        private final String initialCanonicalJson;
        private final String initialBlueId;
        private final Object initialCounter;
        private final String partialCanonicalJson;
        private final String partialBlueId;
        private final Object partialCounter;
        private final List<String> events;

        private ProcessorFailure(String classification,
                                 String normalizedMessage,
                                 long totalGas,
                                 String initialCanonicalJson,
                                 String initialBlueId,
                                 Object initialCounter,
                                 String partialCanonicalJson,
                                 String partialBlueId,
                                 Object partialCounter,
                                 List<String> events) {
            this.classification = classification;
            this.normalizedMessage = normalizedMessage;
            this.totalGas = totalGas;
            this.initialCanonicalJson = initialCanonicalJson;
            this.initialBlueId = initialBlueId;
            this.initialCounter = initialCounter;
            this.partialCanonicalJson = partialCanonicalJson;
            this.partialBlueId = partialBlueId;
            this.partialCounter = partialCounter;
            this.events = events;
        }
    }

    private static final class ProcessorRun {
        private final String canonicalJson;
        private final String blueId;
        private final List<String> events;
        private final long totalGas;

        private ProcessorRun(String canonicalJson, String blueId, List<String> events, long totalGas) {
            this.canonicalJson = canonicalJson;
            this.blueId = blueId;
            this.events = events;
            this.totalGas = totalGas;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ProcessorRun)) {
                return false;
            }
            ProcessorRun that = (ProcessorRun) other;
            return totalGas == that.totalGas
                    && java.util.Objects.equals(canonicalJson, that.canonicalJson)
                    && java.util.Objects.equals(blueId, that.blueId)
                    && java.util.Objects.equals(events, that.events);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(canonicalJson, blueId, events, totalGas);
        }

        @Override
        public String toString() {
            return "ProcessorRun{blueId='" + blueId + "', totalGas=" + totalGas + ", events=" + events + "}";
        }
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
