package blue.contract.model.blink;

import blue.language.model.TypeBlueId;

@TypeBlueId(defaultValueRepositoryDir = "Blink", defaultValueRepositoryKey = "Stockfish Request")
public class StockfishRequest {
    private String fen;
    private Integer depth;

    public String getFen() {
        return fen;
    }

    public StockfishRequest fen(String fen) {
        this.fen = fen;
        return this;
    }

    public Integer getDepth() {
        return depth;
    }

    public StockfishRequest depth(Integer depth) {
        this.depth = depth;
        return this;
    }
}