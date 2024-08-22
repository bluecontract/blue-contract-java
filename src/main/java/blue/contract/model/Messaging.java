package blue.contract.model;

import blue.language.model.Node;

import java.util.Map;

public class Messaging {
    private Map<String, Participant> participants;

    public Map<String, Participant> getParticipants() {
        return participants;
    }

    public Messaging participants(Map<String, Participant> participants) {
        this.participants = participants;
        return this;
    }
}
