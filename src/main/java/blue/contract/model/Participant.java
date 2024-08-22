package blue.contract.model;

import blue.language.model.BlueId;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class Participant {
    private String name;
    @BlueId
    private String timeline;

    public String getName() {
        return name;
    }

    public Participant name(String name) {
        this.name = name;
        return this;
    }

    public String getTimeline() {
        return timeline;
    }

    public Participant timeline(String timeline) {
        this.timeline = timeline;
        return this;
    }
}
