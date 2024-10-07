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
public class ChessAssistedTwoPlayers extends Contract {

    public ChessAssistedTwoPlayers() {}

    public ChessAssistedTwoPlayers(String timelineWhite, String timelineBlack, String externalContractWhiteId, String externalContractBlackId) {
        Map<String, Participant> participants = new HashMap<>();
        participants.put("Player White", new Participant().timeline(timelineWhite));
        participants.put("Player Black", new Participant().timeline(timelineBlack));
        messaging(new Messaging().participants(participants));

        List<ContractSubscription> subscriptions = new ArrayList<>();
        subscriptions.add(new AllEventsExternalContractSubscription(externalContractWhiteId));
        subscriptions.add(new AllEventsExternalContractSubscription(externalContractBlackId));
        subscriptions(subscriptions);

        this.properties = new ChessProperties()
                .assistingContractWhite(externalContractWhiteId)
                .assistingContractBlack(externalContractBlackId);
    }

    private ChessProperties properties;

    public ChessProperties getProperties() {
        return properties;
    }

    public ChessAssistedTwoPlayers properties(ChessProperties properties) {
        this.properties = properties;
        return this;
    }
}
