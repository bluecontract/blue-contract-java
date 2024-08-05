package blue.contract.model;

import blue.language.model.BlueId;
import blue.language.model.Node;

@BlueId("FpdE3U9mgzCGytPDbr11s2hJxTwGcDtMkXiZdBQySjNM")
public class TimelineEntry {
    private String timeline;
    private String timelinePrev;
    private String thread;
    private String threadPrev;
    private Node message;
    private String signature;

    public String getTimeline() {
        return timeline;
    }

    public String getTimelinePrev() {
        return timelinePrev;
    }

    public String getThread() {
        return thread;
    }

    public String getThreadPrev() {
        return threadPrev;
    }

    public Node getMessage() {
        return message;
    }

    public String getSignature() {
        return signature;
    }

    public TimelineEntry timeline(String timeline) {
        this.timeline = timeline;
        return this;
    }

    public TimelineEntry timelinePrev(String timelinePrev) {
        this.timelinePrev = timelinePrev;
        return this;
    }

    public TimelineEntry thread(String thread) {
        this.thread = thread;
        return this;
    }

    public TimelineEntry threadPrev(String threadPrev) {
        this.threadPrev = threadPrev;
        return this;
    }

    public TimelineEntry message(Node message) {
        this.message = message;
        return this;
    }

    public TimelineEntry signature(String signature) {
        this.signature = signature;
        return this;
    }
}