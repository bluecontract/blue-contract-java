package blue.contract.model.chess;

import blue.language.model.BlueId;

public class ChessProperties {
    private String chessboard;
    private String winner;
    private Boolean draw;
    private Boolean gameOver;
    private String playerToMove;
    private String assistingContract;

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

    public String getAssistingContract() {
        return assistingContract;
    }

    public ChessProperties assistingContract(String assistingContract) {
        this.assistingContract = assistingContract;
        return this;
    }
}
