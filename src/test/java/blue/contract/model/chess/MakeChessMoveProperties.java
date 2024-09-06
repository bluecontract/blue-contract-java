package blue.contract.model.chess;

public class MakeChessMoveProperties {
    private String from;
    private String to;

    public MakeChessMoveProperties() {
    }

    public MakeChessMoveProperties(String from, String to) {
        this.from = from;
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public MakeChessMoveProperties from(String from) {
        this.from = from;
        return this;
    }

    public String getTo() {
        return to;
    }

    public MakeChessMoveProperties to(String to) {
        this.to = to;
        return this;
    }
}
