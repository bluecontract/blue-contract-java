package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.BlueDocumentProcessorOptions;
import blue.contract.processor.BlueDocumentProcessors;
import blue.contract.processor.conversation.TimelineProviderSupport;
import blue.contract.processor.conversation.workflow.SequentialWorkflowRunner;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.model.TypeBlueId;
import blue.language.processor.ChannelCheckpointContext;
import blue.language.processor.ChannelEvaluation;
import blue.language.processor.ChannelEvaluationContext;
import blue.language.processor.ChannelProcessor;
import blue.language.processor.DocumentProcessingResult;
import blue.repo.BlueRepository;
import blue.repo.v1_2_0.conversation.ChatMessage;
import blue.repo.v1_2_0.conversation.Timeline;
import blue.repo.v1_2_0.conversation.TimelineChannel;
import blue.repo.v1_2_0.conversation.TimelineEntry;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ChicorySequentialWorkflowExecutionTest {
    private static final String SIMPLE_TIMELINE_CHANNEL_BLUE_ID = "chicory-test-simple-timeline-channel";

    @Test
    void sequentialWorkflowRunsWithChicoryRuntimeInjected() {
        Fixture fixture = configuredFixture();
        Node initialized = fixture.blue.initializeDocument(fixture.blue.preprocess(workflowDocument(fixture.repository))).document();

        DocumentProcessingResult result = fixture.blue.processDocument(initialized,
                timelineEntry(fixture.blue, fixture.repository, "owner", 1, chatMessage("run")));

        assertEquals(BigInteger.TEN, result.document().get("/counter"));
    }

    private static Fixture configuredFixture() {
        BlueRepository repository = BlueRepository.v1_2_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        ChicoryBlueQuickJsRuntime runtime = new ChicoryBlueQuickJsRuntime(ChicoryTestSupport.pinnedConfig(blueQuickJsRoot()));
        BlueDocumentProcessors.registerWith(blue, BlueDocumentProcessorOptions.builder()
                .sequentialWorkflowRunner(SequentialWorkflowRunner.withJavaScriptRuntime(runtime))
                .build());
        blue.registerContractProcessor(new SimpleTimelineChannelProcessor());
        return new Fixture(repository, blue);
    }

    private static Node workflowDocument(BlueRepository repository) {
        Map<String, Node> contracts = new LinkedHashMap<String, Node>();
        contracts.put("owner", channel("owner"));
        contracts.put("direct", new Node()
                .type("Conversation/Sequential Workflow")
                .properties("channel", new Node().value("owner"))
                .properties("steps", new Node().items(Arrays.asList(
                        updateDocumentStep("replace", "/counter", new Node().value("${document('/counter') + 5}")),
                        javaScriptStep("Compute", "return { value: document('/counter') * 2 };"),
                        updateDocumentStep("replace", "/counter", new Node().value("${steps.Compute.value}"))))));

        return new Node()
                .blue(repository.typeAliasBlue())
                .name("Chicory Workflow Counter")
                .properties("counter", new Node().value(0))
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

        Node event = new Node()
                .blue(repository.typeAliasBlue())
                .type(TimelineEntry.qualifiedName())
                .properties("timeline", blue.objectToNode(entry.getTimeline()))
                .properties("timestamp", new Node().value(entry.getTimestamp()))
                .properties("message", entry.getMessage());
        return blue.preprocess(event);
    }

    private static Node chatMessage(String message) {
        ChatMessage chatMessage = new ChatMessage().message(message);
        return new Node()
                .type(ChatMessage.qualifiedName())
                .properties("message", new Node().value(chatMessage.getMessage()));
    }

    private static Path blueQuickJsRoot() {
        return ChicoryTestSupport.blueQuickJsRoot("blue-quickjs checkout is required for workflow tests");
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

    private static final class Fixture {
        private final BlueRepository repository;
        private final Blue blue;

        private Fixture(BlueRepository repository, Blue blue) {
            this.repository = repository;
            this.blue = blue;
        }
    }
}
