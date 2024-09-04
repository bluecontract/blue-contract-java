package blue.contract.simulator;

import blue.contract.simulator.model.InitiateTimelineAction;
import blue.contract.simulator.model.SimulatorTimelineEntry;
import blue.language.Blue;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class SimulatorMT {
    private final Map<String, Timeline> timelines;
    private final List<Subscription> subscriptions;
    private final AtomicInteger globalTickSequence;
    private final Blue blue;
    private final ExecutorService executorService;

    public SimulatorMT(Blue blue) {
        this.timelines = new ConcurrentHashMap<>();
        this.subscriptions = new CopyOnWriteArrayList<>();
        this.globalTickSequence = new AtomicInteger(0);
        this.blue = blue;
        this.executorService = Executors.newCachedThreadPool();
        System.out.println("SimulatorMT initialized with Blue instance");
    }

    public String createTimeline(String owner) {
        System.out.println("Creating timeline for owner: " + owner);
        InitiateTimelineAction action = new InitiateTimelineAction().owner(owner);
        SimulatorTimelineEntry<Object> entry = new SimulatorTimelineEntry<>()
                .message(action)
                .tickSequence(globalTickSequence.getAndIncrement());
        String blueId = blue.calculateBlueId(entry);
        Timeline timeline = new Timeline(blueId);
        timelines.put(blueId, timeline);
        timeline.appendEntry(entry);
        System.out.println("Timeline created with ID: " + blueId);
        return blueId;
    }

    public String appendEntry(String timelineId, Object message) {
        return appendEntry(timelineId, null, message);
    }

    public String appendEntry(String timelineId, String threadId, Object message) {
        System.out.println("Appending entry to timeline: " + timelineId + ", thread: " + threadId);
        Timeline timeline = timelines.get(timelineId);
        if (timeline == null) {
            throw new IllegalArgumentException("Timeline with ID " + timelineId + " does not exist");
        }

        SimulatorTimelineEntry<Object> newEntry = createEntry(timelineId, threadId, message);
        timeline.appendEntry(newEntry);
        String blueId = blue.calculateBlueId(newEntry);
        System.out.println("Appended entry with ID: " + blueId);

        // Ensure subscribers are notified synchronously
        notifySubscribers(newEntry);

        return blueId;
    }

    private void notifySubscribers(SimulatorTimelineEntry<Object> entry) {
        System.out.println("Notifying subscribers [" + subscriptions.size() + "] for entry: " + blue.calculateBlueId(entry));
        for (Subscription subscription : subscriptions) {
            if (subscription.filter.test(entry)) {
                System.out.println("Subscription matched. Executing consumer.");
                // Execute the consumer synchronously
                subscription.consumer.accept(entry);
            }
        }
    }


    private SimulatorTimelineEntry<Object> createEntry(String timelineId, String threadId, Object message) {
        Timeline timeline = timelines.get(timelineId);
        String prevEntryId = timeline.getLastEntryId();
        String threadPrev = (threadId != null) ? timeline.getLastThreadEntryId(threadId) : null;

        return new SimulatorTimelineEntry<>()
                .timeline(timelineId)
                .timelinePrev(prevEntryId)
                .thread(threadId)
                .threadPrev(threadPrev)
                .message(message)
                .tickSequence(globalTickSequence.getAndIncrement());
    }

    public void subscribe(Predicate<SimulatorTimelineEntry<Object>> filter, TimelineEntryConsumer consumer) {
        subscriptions.add(new Subscription(filter, consumer));
        System.out.println("New subscription added. Total subscriptions: " + subscriptions.size());
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private class Timeline {
        private final String id;
        private final BlockingQueue<SimulatorTimelineEntry<Object>> eventQueue;
        private volatile String lastEntryId;
        private final Map<String, String> lastThreadEntryIds;

        public Timeline(String id) {
            this.id = id;
            this.eventQueue = new LinkedBlockingQueue<>();
            this.lastThreadEntryIds = new ConcurrentHashMap<>();
        }

        public void appendEntry(SimulatorTimelineEntry<Object> entry) {
            eventQueue.offer(entry);
            lastEntryId = blue.calculateBlueId(entry);
            if (entry.getThread() != null) {
                lastThreadEntryIds.put(entry.getThread(), lastEntryId);
            }
        }

        public String getLastEntryId() {
            return lastEntryId;
        }

        public String getLastThreadEntryId(String threadId) {
            return lastThreadEntryIds.get(threadId);
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