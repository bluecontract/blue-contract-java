package blue.coordination.processor;

import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.ChannelCheckpointContext;
import blue.language.processor.ChannelEvaluation;
import blue.language.processor.ChannelEvaluationContext;
import blue.language.processor.ChannelProcessor;
import blue.repo.BlueRepository;
import blue.repo.coordination.ChatMessage;
import blue.repo.coordination.Timeline;
import blue.repo.coordination.TimelineChannel;
import blue.repo.coordination.TimelineEntry;

import java.math.BigInteger;

public final class TestTimelineProvider {
    private TestTimelineProvider() {
    }

    public static Blue registerWith(Blue blue) {
        blue.registerContractProcessor(TimelineChannel.blueId(), new SimpleTimelineChannelProcessor());
        return blue;
    }

    public static Node channel(String timelineId) {
        Node channel = new Node().type(TimelineChannel.qualifiedName());
        if (timelineId != null) {
            channel.properties("timelineId", new Node().value(timelineId));
        }
        return channel;
    }

    public static Node timelineEntry(Blue blue,
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
        Node aliasesResolved = new RepositoryTypeAliasPreprocessor(
                CoordinationTestResources.testTypeAliases(repository)).preprocess(event);
        return blue.preprocess(aliasesResolved).blue(null);
    }

    public static Node chatMessage(String message) {
        ChatMessage chatMessage = new ChatMessage().message(message);
        return new Node()
                .type(ChatMessage.qualifiedName())
                .properties("message", new Node().value(chatMessage.getMessage()));
    }

    public static final class SimpleTimelineChannelProcessor implements ChannelProcessor<TimelineChannel> {
        @Override
        public Class<TimelineChannel> contractType() {
            return TimelineChannel.class;
        }

        @Override
        public ChannelEvaluation evaluate(TimelineChannel contract, ChannelEvaluationContext context) {
            return TimelineProviderSupport.evaluateTimelineEntry(contract, context);
        }

        @Override
        public String eventId(TimelineChannel contract, ChannelEvaluationContext context) {
            return TimelineProviderSupport.eventId(context.event());
        }

        @Override
        public boolean isNewerEvent(TimelineChannel contract, ChannelCheckpointContext context) {
            return TimelineProviderSupport.isNewerOrSameTimelineEvent(context);
        }
    }
}
