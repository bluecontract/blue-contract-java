package blue.contract.model;

import blue.language.model.TypeBlueId;

import java.util.HashMap;
import java.util.Map;

@TypeBlueId(defaultValueRepositoryDir = "Chess")
public class Chess extends Contract {

    public Chess() {}

    public Chess(String timelineWhite, String timelineBlack) {
        Map<String, Participant> participants = new HashMap<>();
        participants.put("Player White", new Participant().timeline(timelineWhite));
        participants.put("Player Black", new Participant().timeline(timelineBlack));
        messaging(new Messaging().participants(participants));
    }

    private ChessProperties properties;

    public ChessProperties getProperties() {
        return properties;
    }

    public Chess properties(ChessProperties properties) {
        this.properties = properties;
        return this;
    }
}
