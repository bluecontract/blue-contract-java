package blue.contract.model.blink;

import blue.language.model.TypeBlueId;

@TypeBlueId(defaultValueRepositoryDir = "simulator")
public class InitiateTimelineAction {
    private String owner;

    public InitiateTimelineAction() {
    }

    public InitiateTimelineAction(String owner) {
        this.owner = owner;
    }

    public String getOwner() {
        return owner;
    }

    public InitiateTimelineAction owner(String owner) {
        this.owner = owner;
        return this;
    }
}
