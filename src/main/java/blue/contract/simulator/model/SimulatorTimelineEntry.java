package blue.contract.simulator.model;

import blue.contract.model.TimelineEntry;
import blue.language.model.TypeBlueId;

@TypeBlueId(defaultValueRepositoryDir = "simulator")
public class SimulatorTimelineEntry<T> extends TimelineEntry<T> {
    private Integer tickSequence;

    public Integer getTickSequence() {
        return tickSequence;
    }

    public SimulatorTimelineEntry<T> tickSequence(Integer tickSequence) {
        this.tickSequence = tickSequence;
        return this;
    }


    @Override
    public SimulatorTimelineEntry<T> message(T message) {
        super.message(message);
        return this;
    }

    @Override
    public SimulatorTimelineEntry<T> timeline(String timeline) {
        super.timeline(timeline);
        return this;
    }

    @Override
    public SimulatorTimelineEntry<T> timelinePrev(String timelinePrev) {
        super.timelinePrev(timelinePrev);
        return this;
    }

    @Override
    public SimulatorTimelineEntry<T> thread(String thread) {
        super.thread(thread);
        return this;
    }

    @Override
    public SimulatorTimelineEntry<T> threadPrev(String threadPrev) {
        super.threadPrev(threadPrev);
        return this;
    }

    @Override
    public SimulatorTimelineEntry<T> signature(String signature) {
        super.signature(signature);
        return this;
    }
}
