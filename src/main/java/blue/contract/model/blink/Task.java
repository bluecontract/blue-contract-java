package blue.contract.model.blink;

import blue.contract.model.Contract;
import blue.contract.model.Messaging;
import blue.contract.model.Participant;
import blue.language.model.TypeBlueId;

import java.util.HashMap;
import java.util.Map;

@TypeBlueId(defaultValueRepositoryDir = "Blink")
public class Task extends Contract {

    private TaskProperties properties;

    public Task() {}

    public Task(String userTimeline, String assistantTimeline) {
        Map<String, Participant> participants = new HashMap<>();
        participants.put("User", new Participant().timeline(userTimeline));
        participants.put("Assistant", new Participant().timeline(assistantTimeline));
        messaging(new Messaging().participants(participants));
    }

    public TaskProperties getProperties() {
        return properties;
    }

    public Task properties(TaskProperties properties) {
        this.properties = properties;
        return this;
    }
}