package blue.contract.model.chess;

import blue.contract.model.Contract;
import blue.contract.model.Messaging;
import blue.contract.model.Participant;
import blue.contract.model.subscription.AllEventsExternalContractSubscription;
import blue.contract.model.subscription.ContractSubscription;
import blue.language.model.TypeBlueId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TypeBlueId(defaultValueRepositoryDir = "Chess")
public class ChessAssistedRemotely extends Contract {

    public ChessAssistedRemotely() {}

    public ChessAssistedRemotely(String timelineWhite, String timelineBlack, String externalContractId) {
        Map<String, Participant> participants = new HashMap<>();
        participants.put("Player White", new Participant().timeline(timelineWhite));
        participants.put("Player Black", new Participant().timeline(timelineBlack));
        messaging(new Messaging().participants(participants));

        List<ContractSubscription> subscriptions = new ArrayList<>();
        subscriptions.add(new AllEventsExternalContractSubscription(externalContractId));
        subscriptions(subscriptions);
    }

    private ChessProperties properties;

    public ChessProperties getProperties() {
        return properties;
    }

    public ChessAssistedRemotely properties(ChessProperties properties) {
        this.properties = properties;
        return this;
    }
}
