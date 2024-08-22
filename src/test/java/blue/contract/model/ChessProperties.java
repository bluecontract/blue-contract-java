package blue.contract.model;

public class ChessProperties {
    private String chessboard;
    private String winner;
    private Boolean draw;
    private Boolean gameOver;
    private String playerToMove;

    public String getChessboard() {
        return chessboard;
    }

    public ChessProperties chessboard(String chessboard) {
        this.chessboard = chessboard;
        return this;
    }

    public String getWinner() {
        return winner;
    }

    public ChessProperties winner(String winner) {
        this.winner = winner;
        return this;
    }

    public Boolean getDraw() {
        return draw;
    }

    public ChessProperties draw(Boolean draw) {
        this.draw = draw;
        return this;
    }

    public Boolean getGameOver() {
        return gameOver;
    }

    public ChessProperties gameOver(Boolean gameOver) {
        this.gameOver = gameOver;
        return this;
    }

    public String getPlayerToMove() {
        return playerToMove;
    }

    public ChessProperties playerToMove(String playerToMove) {
        this.playerToMove = playerToMove;
        return this;
    }
}
