package blue.contract.simulator;

import blue.contract.model.blink.InitiateTimelineAction;
import blue.contract.model.blink.SimulatorTimelineEntry;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.NodeToMapListOrValue;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class Simulator {
    private Map<String, List<SimulatorTimelineEntry<Object>>> timelines;
    private List<Subscription> subscriptions;
    private int globalTickSequence;
    private Blue blue;

    public Simulator(Blue blue) {
        this.timelines = new HashMap<>();
        this.subscriptions = new ArrayList<>();
        this.globalTickSequence = 0;
        this.blue = blue;
        System.out.println("Simulator initialized with Blue instance");
    }

    public String createTimeline(String owner) {
        System.out.println("Creating timeline for owner: " + owner);
        InitiateTimelineAction action = new InitiateTimelineAction().owner(owner);
        SimulatorTimelineEntry<Object> entry = new SimulatorTimelineEntry<>()
                .message(action)
                .tickSequence(globalTickSequence++);
        String blueId = blue.calculateBlueId(entry);
        timelines.put(blueId, new ArrayList<>(List.of(entry)));
        System.out.println("Timeline created with ID: " + blueId);
        notifySubscribers(entry);
        return blueId;
    }

    public String appendEntry(String timelineId, Object message) {
        System.out.println("Appending entry to timeline: " + timelineId);
        return appendEntry(timelineId, null, message);
    }

    public String appendEntry(String timelineId, String threadId, Object message) {
        System.out.println("Appending entry to timeline: " + timelineId + ", thread: " + threadId);
        List<String> ids = appendEntriesInternal(timelineId, threadId, Collections.singletonList(message));
        return ids.get(0);
    }

    public <T> List<String> appendEntries(String timelineId, List<T> messages) {
        System.out.println("Appending multiple entries to timeline: " + timelineId);
        return appendEntries(timelineId, null, messages);
    }

    public <T> List<String> appendEntries(String timelineId, String threadId, List<T> messages) {
        System.out.println("Appending multiple entries to timeline: " + timelineId + ", thread: " + threadId);
        return appendEntriesInternal(timelineId, threadId, (List<Object>)(List<?>)messages);
    }

    private List<String> appendEntriesInternal(String timelineId, String threadId, List<Object> messages) {
        List<SimulatorTimelineEntry<Object>> timeline = timelines.get(timelineId);
        if (timeline == null) {
            System.out.println("Error: Timeline with ID " + timelineId + " does not exist");
            throw new IllegalArgumentException("Timeline with ID " + timelineId + " does not exist");
        }

        if (messages == null || messages.isEmpty()) {
            System.out.println("No messages to append");
            return new ArrayList<>();
        }

        SimulatorTimelineEntry<Object> prevEntry = timeline.get(timeline.size() - 1);
        String prevEntryId = blue.calculateBlueId(prevEntry);

        String threadPrev = null;
        if (threadId != null) {
            for (int i = timeline.size() - 1; i >= 0; i--) {
                SimulatorTimelineEntry<Object> entry = timeline.get(i);
                if (threadId.equals(entry.getThread())) {
                    threadPrev = blue.calculateBlueId(entry);
                    System.out.println("Found previous entry in thread: " + threadPrev);
                    break;
                }
            }
        }

        int batchTickSequence = globalTickSequence++;
        List<String> newEntryIds = new ArrayList<>();

        for (Object message : messages) {

            SimulatorTimelineEntry<Object> newEntry = new SimulatorTimelineEntry<>()
                    .timeline(timelineId)
                    .timelinePrev(prevEntryId)
                    .thread(threadId)
                    .threadPrev(threadPrev)
                    .message(blue.clone(message))
                    .tickSequence(batchTickSequence);

            String blueId = blue.calculateBlueId(newEntry);
            timeline.add(newEntry);
            newEntryIds.add(blueId);
            System.out.println("Appended entry with ID: " + blueId);
            notifySubscribers(newEntry);

            prevEntryId = blueId;
            if (threadId != null) {
                threadPrev = blueId;
            }
        }

        return newEntryIds;
    }

    public void subscribe(Predicate<SimulatorTimelineEntry<Object>> filter, TimelineEntryConsumer consumer) {
        subscriptions.add(new Subscription(filter, consumer));
        System.out.println("New subscription added. Total subscriptions: " + subscriptions.size());
    }

    private void notifySubscribers(SimulatorTimelineEntry<Object> entry) {
        System.out.println("Notifying subscribers [" + subscriptions.size() + "] for entry: " + blue.calculateBlueId(entry));
        for (Subscription subscription : subscriptions) {
            if (subscription.filter.test(entry)) {
                System.out.println("Subscription matched. Executing consumer.");
                subscription.consumer.accept(entry);
            }
        }
    }

    public Map<String, List<SimulatorTimelineEntry<Object>>> getTimelines() {
        System.out.println("Returning copy of all timelines");
        return new HashMap<>(timelines);
    }

    public int getTimelineCount() {
        int count = timelines.size();
        System.out.println("Current timeline count: " + count);
        return count;
    }

    public <T> T getMessageFromLastTimelineEntry(String timelineId, Class<T> clazz) {
        System.out.println("Getting message from last timeline entry for timeline: " + timelineId);

        List<SimulatorTimelineEntry<Object>> timeline = timelines.get(timelineId);
        if (timeline == null || timeline.isEmpty()) {
            System.out.println("Error: Timeline with ID " + timelineId + " does not exist or is empty");
            return null;
        }

        SimulatorTimelineEntry<Object> lastEntry = timeline.get(timeline.size() - 1);
        Object message = lastEntry.getMessage();

        if (message == null) {
            System.out.println("Last entry has no message");
            return null;
        }

        if (!clazz.isInstance(message)) {
            System.out.println("Error: Message is not an instance of " + clazz.getSimpleName());
            return null;
        }

        T typedMessage = clazz.cast(message);
        System.out.println("Retrieved message of type " + clazz.getSimpleName() + " from last entry");
        return typedMessage;
    }

    public void save(String timelineId, int skipEntries, String directory, String filePrefix) throws IOException {
        List<SimulatorTimelineEntry<Object>> timeline = timelines.get(timelineId);
        if (timeline == null) {
            throw new IllegalArgumentException("Timeline with ID " + timelineId + " does not exist");
        }
        for (int i = skipEntries; i < timeline.size(); i++) {
            SimulatorTimelineEntry<Object> entry = timeline.get(i);
            Node entryNode = JSON_MAPPER.convertValue(entry, Node.class);

            File outputFile = new File(directory + "/" + filePrefix + "_" + (i - skipEntries + 1) + "_entry.blue");
            YAML_MAPPER.writeValue(outputFile, NodeToMapListOrValue.get(entryNode, NodeToMapListOrValue.Strategy.SIMPLE));
        }
    }

    private static class Subscription {
        Predicate<SimulatorTimelineEntry<Object>> filter;
        TimelineEntryConsumer consumer;

        Subscription(Predicate<SimulatorTimelineEntry<Object>> filter, TimelineEntryConsumer consumer) {
            this.filter = filter;
            this.consumer = consumer;
        }
    }

    public interface TimelineEntryConsumer {
        void accept(SimulatorTimelineEntry<Object> entry);
    }
}