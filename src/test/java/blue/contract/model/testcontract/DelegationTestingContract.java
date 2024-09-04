package blue.contract.model.testcontract;

import blue.contract.model.Contract;
import blue.contract.model.Messaging;
import blue.contract.model.Participant;
import blue.language.model.TypeBlueId;

import java.util.HashMap;
import java.util.Map;

@TypeBlueId(defaultValueRepositoryDir = "Testing Delegations")
public class DelegationTestingContract extends Contract {
    public DelegationTestingContract() {}

    public DelegationTestingContract(String timelineAlice, String timelineBob) {
        Map<String, Participant> participants = new HashMap<>();
        participants.put("Alice", new Participant().timeline(timelineAlice));
        participants.put("Bob", new Participant().timeline(timelineBob));
        messaging(new Messaging().participants(participants));
    }
}