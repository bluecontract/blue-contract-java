package blue.contract.model.blink;

import blue.language.model.TypeBlueId;

import java.math.BigDecimal;

@TypeBlueId(defaultValueRepositoryDir = "Blink", defaultValueRepositoryKey = "Stockfish Response")
public class StockfishResponse {
    private String text;
    private BigDecimal eval;
    private String move;
    private String fen;
    private Integer depth;
    private BigDecimal winChance;
    private String from;
    private String to;

    public String getText() {
        return text;
    }

    public StockfishResponse text(String text) {
        this.text = text;
        return this;
    }

    public BigDecimal getEval() {
        return eval;
    }

    public StockfishResponse eval(BigDecimal eval) {
        this.eval = eval;
        return this;
    }

    public String getMove() {
        return move;
    }

    public StockfishResponse move(String move) {
        this.move = move;
        return this;
    }

    public String getFen() {
        return fen;
    }

    public StockfishResponse fen(String fen) {
        this.fen = fen;
        return this;
    }

    public Integer getDepth() {
        return depth;
    }

    public StockfishResponse depth(Integer depth) {
        this.depth = depth;
        return this;
    }

    public BigDecimal getWinChance() {
        return winChance;
    }

    public StockfishResponse winChance(BigDecimal winChance) {
        this.winChance = winChance;
        return this;
    }

    public String getFrom() {
        return from;
    }

    public StockfishResponse from(String from) {
        this.from = from;
        return this;
    }

    public String getTo() {
        return to;
    }

    public StockfishResponse to(String to) {
        this.to = to;
        return this;
    }
}
