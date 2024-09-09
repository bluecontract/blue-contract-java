package blue.contract.model.blink;

import blue.contract.model.Messaging;
import blue.contract.model.Participant;
import blue.language.model.TypeBlueId;

import java.util.HashMap;
import java.util.Map;

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
