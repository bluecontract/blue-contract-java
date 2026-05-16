package blue.contract.processor.conversation;

import blue.language.model.Node;
import blue.language.processor.ChannelCheckpointContext;
import blue.language.processor.ChannelDelivery;
import blue.language.processor.ChannelEvaluation;
import blue.language.processor.ChannelEvaluationContext;
import blue.language.processor.ChannelProcessor;
import blue.language.processor.model.ChannelEventCheckpoint;
import blue.language.processor.model.ChannelContract;
import blue.language.processor.model.MarkerContract;
import blue.repo.v1_3_0.conversation.CompositeTimelineChannel;
import java.util.ArrayList;
import java.util.List;

public final class CompositeTimelineChannelProcessor implements ChannelProcessor<CompositeTimelineChannel> {
    @Override
    public Class<CompositeTimelineChannel> contractType() {
        return CompositeTimelineChannel.class;
    }

    @Override
    public ChannelEvaluation evaluate(CompositeTimelineChannel contract, ChannelEvaluationContext context) {
        List<String> channels = contract.getChannels();
        if (channels == null || channels.isEmpty()) {
            return ChannelEvaluation.noMatch();
        }
        List<ChannelDelivery> deliveries = new ArrayList<ChannelDelivery>();
        for (String childKey : channels) {
            String key = trimToNull(childKey);
            if (key == null) {
                continue;
            }
            if (key.equals(context.bindingKey())) {
                throw new IllegalStateException("Composite Timeline Channel '" + context.bindingKey()
                        + "' cannot include itself");
            }
            ChannelContract child = context.channel(key);
            if (child == null) {
                throw new IllegalStateException("Composite Timeline Channel '" + context.bindingKey()
                        + "' references missing child channel '" + key + "'");
            }
            ChannelProcessor<? extends ChannelContract> processor = context.channelProcessor(key);
            if (processor == null) {
                throw new IllegalStateException("No processor registered for Composite Timeline Channel child '"
                        + key + "'");
            }
            ChannelEvaluation childEvaluation = evaluateChild(processor, child, context.forBindingKey(key));
            if (childEvaluation == null || !childEvaluation.matches()) {
                continue;
            }
            Node deliveryEvent = childEvaluation.event() != null
                    ? childEvaluation.event()
                    : context.event();
            if (deliveryEvent == null) {
                continue;
            }
            String checkpointKey = compositeCheckpointKey(context.bindingKey(), key);
            Boolean shouldProcess = shouldProcessChild(processor, child, context, checkpointKey, childEvaluation);
            deliveries.add(ChannelDelivery.of(withCompositeMetadata(deliveryEvent, key),
                    childEvaluation.eventId(),
                    checkpointKey,
                    shouldProcess));
        }
        return ChannelEvaluation.matchDeliveries(deliveries);
    }

    static String compositeCheckpointKey(String compositeKey, String childKey) {
        return compositeKey + "::" + childKey;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ChannelEvaluation evaluateChild(ChannelProcessor processor,
                                            ChannelContract child,
                                            ChannelEvaluationContext context) {
        return processor.evaluate(child, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Boolean shouldProcessChild(ChannelProcessor processor,
                                       ChannelContract child,
                                       ChannelEvaluationContext context,
                                       String checkpointKey,
                                       ChannelEvaluation childEvaluation) {
        MarkerContract marker = context.markers().get("checkpoint");
        ChannelEventCheckpoint checkpoint = marker instanceof ChannelEventCheckpoint
                ? (ChannelEventCheckpoint) marker
                : null;
        Node lastEvent = checkpoint != null ? checkpoint.lastEvent(checkpointKey) : null;
        String lastSignature = checkpoint != null ? checkpoint.lastSignature(checkpointKey) : null;
        ChannelCheckpointContext checkpointContext = ChannelCheckpointContext.of(
                context.scopePath(),
                checkpointKey,
                context.event(),
                childEvaluation.eventId(),
                lastEvent,
                lastSignature,
                context.markers());
        return processor.isNewerEvent(child, checkpointContext);
    }

    private Node withCompositeMetadata(Node event, String childKey) {
        Node copy = event.clone();
        Node meta = property(copy, "meta");
        if (meta == null) {
            meta = new Node();
            copy.properties("meta", meta);
        }
        meta.properties("compositeSourceChannelKey", new Node().value(childKey));
        return copy;
    }

    private Node property(Node node, String key) {
        if (node == null || node.getProperties() == null) {
            return null;
        }
        return node.getProperties().get(key);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
