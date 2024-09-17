package blue.contract.simulator;

@FunctionalInterface
public interface LastEntryMessageRetriever {
    <T> T getMessageFromLastTimelineEntry(String timelineId, Class<T> clazz);
}