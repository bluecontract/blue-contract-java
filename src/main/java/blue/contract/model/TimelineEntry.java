package blue.contract.model;

import blue.language.model.BlueId;
import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class TimelineEntry<T> {
    @BlueId
    protected String timeline;
    @BlueId
    protected String timelinePrev;
    @BlueId
    protected String thread;
    @BlueId
    protected String threadPrev;
    protected T message;
    protected String signature;

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

    public T getMessage() {
        return message;
    }

    public String getSignature() {
        return signature;
    }

    public TimelineEntry<T> timeline(String timeline) {
        this.timeline = timeline;
        return this;
    }

    public TimelineEntry<T> timelinePrev(String timelinePrev) {
        this.timelinePrev = timelinePrev;
        return this;
    }

    public TimelineEntry<T> thread(String thread) {
        this.thread = thread;
        return this;
    }

    public TimelineEntry<T> threadPrev(String threadPrev) {
        this.threadPrev = threadPrev;
        return this;
    }

    public TimelineEntry<T> message(T message) {
        this.message = message;
        return this;
    }

    public TimelineEntry<T> signature(String signature) {
        this.signature = signature;
        return this;
    }
}