package blue.contract.model.testcontract;

import blue.contract.model.Contract;
import blue.contract.model.Messaging;
import blue.contract.model.Participant;
import blue.language.model.TypeBlueId;

import java.util.HashMap;
import java.util.Map;

@TypeBlueId(defaultValueRepositoryDir = "Blink", defaultValueRepositoryKey = "Sample API Contract")
public class SampleAPIContract extends Contract {
    public SampleAPIContract() {}

    public SampleAPIContract(String timelineAssistant) {
        Map<String, Participant> participants = new HashMap<>();
        participants.put("Assistant", new Participant().timeline(timelineAssistant));
        messaging(new Messaging().participants(participants));
    }
}