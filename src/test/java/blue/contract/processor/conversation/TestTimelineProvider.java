package blue.contract.processor.conversation;

import blue.language.Blue;
import blue.language.model.Node;
import blue.language.model.TypeBlueId;
import blue.language.processor.ChannelCheckpointContext;
import blue.language.processor.ChannelEvaluation;
import blue.language.processor.ChannelEvaluationContext;
import blue.language.processor.ChannelProcessor;
import blue.repo.BlueRepository;
import blue.repo.conversation.ChatMessage;
import blue.repo.conversation.Timeline;
import blue.repo.conversation.TimelineChannel;
import blue.repo.conversation.TimelineEntry;

import java.math.BigInteger;

public final class TestTimelineProvider {
    public static final String SIMPLE_TIMELINE_CHANNEL_BLUE_ID = "test-simple-timeline-channel";

    private TestTimelineProvider() {
    }

    public static Blue registerWith(Blue blue) {
        blue.registerContractProcessor(new SimpleTimelineChannelProcessor());
        return blue;
    }

    public static Node channel(String timelineId) {
        Node channel = new Node().type(new Node().blueId(SIMPLE_TIMELINE_CHANNEL_BLUE_ID));
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
        return blue.preprocess(event);
    }

    public static Node chatMessage(String message) {
        ChatMessage chatMessage = new ChatMessage().message(message);
        return new Node()
                .type(ChatMessage.qualifiedName())
                .properties("message", new Node().value(chatMessage.getMessage()));
    }

    @TypeBlueId(TestTimelineProvider.SIMPLE_TIMELINE_CHANNEL_BLUE_ID)
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
}
