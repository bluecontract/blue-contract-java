package blue.contract.model.testcontract;

import blue.contract.model.Contract;
import blue.contract.model.Messaging;
import blue.contract.model.Participant;
import blue.language.model.TypeBlueId;

import java.util.HashMap;
import java.util.Map;

@TypeBlueId(defaultValueRepositoryDir = "Blink")
public class TempSample extends Contract {
    public TempSample() {}

    public TempSample(String timelineAlice, String timelineBob) {
        Map<String, Participant> participants = new HashMap<>();
        participants.put("Alice", new Participant().timeline(timelineAlice));
        participants.put("Bob", new Participant().timeline(timelineBob));
        messaging(new Messaging().participants(participants));
    }
}