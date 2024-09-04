package blue.contract.model.blink;

import blue.contract.model.Messaging;
import blue.contract.model.Participant;
import blue.language.model.TypeBlueId;

import java.util.HashMap;
import java.util.Map;

@TypeBlueId(defaultValueRepositoryDir = "Blink")
public class SampleTask extends Task {
    public SampleTask(String assistantTimeline) {
        if (getProperties() == null) {
            properties(new TaskProperties());
        }
        getProperties().assistantTimeline(assistantTimeline);
    }
}
