package blue.contract.model.chess;

import blue.language.model.TypeBlueId;

@TypeBlueId(defaultValueRepositoryDir = "Chess")
public class MakeChessMoveTask {

    private MakeChessMoveProperties properties;

    public MakeChessMoveTask() {
    }

    public MakeChessMoveTask(String from, String to) {
        properties(new MakeChessMoveProperties(from, to));
    }

    public MakeChessMoveProperties getProperties() {
        return properties;
    }

    public MakeChessMoveTask properties(MakeChessMoveProperties properties) {
        this.properties = properties;
        return this;
    }
}
