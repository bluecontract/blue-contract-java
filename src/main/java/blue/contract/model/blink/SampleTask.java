package blue.contract.model.blink;

import blue.language.model.TypeBlueId;

@TypeBlueId(defaultValueRepositoryDir = "Blink")
public class SampleTask {

    private SampleTaskProperties properties;

    public SampleTask() {}
    
    public SampleTask(String assistantTimeline, String fen) {
        if (getProperties() == null) {
            properties(new SampleTaskProperties());
        }
        getProperties().assistantTimeline(assistantTimeline);
        getProperties().fen(fen);
    }

    public SampleTaskProperties getProperties() {
        return properties;
    }

    public SampleTask properties(SampleTaskProperties properties) {
        this.properties = properties;
        return this;
    }

}
