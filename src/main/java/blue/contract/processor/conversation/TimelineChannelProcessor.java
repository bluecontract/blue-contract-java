package blue.contract.processor.conversation;

import blue.language.processor.ChannelCheckpointContext;
import blue.language.processor.ChannelEvaluation;
import blue.language.processor.ChannelEvaluationContext;
import blue.language.processor.ChannelProcessor;
import blue.repo.conversation.TimelineChannel;

public final class TimelineChannelProcessor implements ChannelProcessor<TimelineChannel> {
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
