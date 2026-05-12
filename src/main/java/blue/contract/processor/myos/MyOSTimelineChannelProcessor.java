package blue.contract.processor.myos;

import blue.contract.processor.conversation.TimelineProviderSupport;
import blue.language.model.Node;
import blue.language.processor.ChannelCheckpointContext;
import blue.language.processor.ChannelEvaluation;
import blue.language.processor.ChannelEvaluationContext;
import blue.language.processor.ChannelProcessor;
import blue.repo.v1_2_0.myos.MyOSTimelineChannel;
import blue.repo.v1_2_0.myos.MyOSTimelineEntry;

public final class MyOSTimelineChannelProcessor implements ChannelProcessor<MyOSTimelineChannel> {
    @Override
    public Class<MyOSTimelineChannel> contractType() {
        return MyOSTimelineChannel.class;
    }

    @Override
    public ChannelEvaluation evaluate(MyOSTimelineChannel contract, ChannelEvaluationContext context) {
        Node eventNode = context.event();
        if (!TimelineProviderSupport.hasType(eventNode, MyOSTimelineEntry.blueId(), MyOSTimelineEntry.qualifiedName())) {
            return ChannelEvaluation.noMatch();
        }
        if (!TimelineProviderSupport.matchesTimelineId(contract, eventNode)) {
            return ChannelEvaluation.noMatch();
        }
        Node actor = TimelineProviderSupport.property(eventNode, "actor");
        if (!matchesConstraint(contract.getAccountId(), TimelineProviderSupport.textProperty(actor, "accountId"))) {
            return ChannelEvaluation.noMatch();
        }
        if (!matchesConstraint(contract.getEmail(), TimelineProviderSupport.textProperty(actor, "email"))) {
            return ChannelEvaluation.noMatch();
        }
        if (!TimelineProviderSupport.matchesEventFilter(contract, eventNode)) {
            return ChannelEvaluation.noMatch();
        }
        return ChannelEvaluation.match(eventNode);
    }

    @Override
    public String eventId(MyOSTimelineChannel contract, ChannelEvaluationContext context) {
        return TimelineProviderSupport.eventId(context.event());
    }

    @Override
    public boolean isNewerEvent(MyOSTimelineChannel contract, ChannelCheckpointContext context) {
        return TimelineProviderSupport.isNewerOrSameTimelineEvent(context);
    }

    private boolean matchesConstraint(String expected, String actual) {
        String trimmed = trimToNull(expected);
        return trimmed == null || trimmed.equals(actual);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
