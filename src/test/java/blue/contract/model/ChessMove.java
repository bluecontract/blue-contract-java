package blue.contract.model;

import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = "Chess")
public class ChessMove {
    private String from;
    private String to;

    public ChessMove(String from, String to) {
        this.from = from;
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public ChessMove from(String from) {
        this.from = from;
        return this;
    }

    public String getTo() {
        return to;
    }

    public ChessMove to(String to) {
        this.to = to;
        return this;
    }
}
