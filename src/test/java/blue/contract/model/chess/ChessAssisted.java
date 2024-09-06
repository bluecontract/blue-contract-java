package blue.contract.model.chess;

import blue.contract.model.Contract;
import blue.contract.model.Messaging;
import blue.contract.model.Participant;
import blue.language.model.TypeBlueId;

import java.util.HashMap;
import java.util.Map;

@TypeBlueId(defaultValueRepositoryDir = "Chess")
public class ChessAssisted extends Contract {

    private ChessProperties properties;

    public ChessAssisted() {}

    public ChessAssisted(String timelineWhite, String timelineBlack, String timelineAssistant) {
        Map<String, Participant> participants = new HashMap<>();
        participants.put("Player White", new Participant().timeline(timelineWhite));
        participants.put("Player Black", new Participant().timeline(timelineBlack));
        participants.put("Assistant", new Participant().timeline(timelineAssistant));
        messaging(new Messaging().participants(participants));
    }

    public ChessProperties getProperties() {
        return properties;
    }

    public ChessAssisted properties(ChessProperties properties) {
        this.properties = properties;
        return this;
    }
}
