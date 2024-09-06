package blue.contract;

import blue.contract.model.ContractUpdateAction;
import blue.contract.model.action.InitiateContractAction;
import blue.contract.model.action.InitiateContractProcessingAction;
import blue.contract.model.chess.Chess;
import blue.contract.model.chess.ChessAssisted;
import blue.contract.model.chess.ChessMove;
import blue.contract.simulator.ContractRunner2;
import blue.contract.simulator.Simulator;
import blue.language.Blue;
import blue.language.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static blue.contract.utils.Utils.testBlue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChessAssistedTest {

    private Simulator simulator;
    private Blue blue;

    @BeforeEach
    void setUp() throws IOException {
        blue = testBlue();
        simulator = new Simulator(blue);
    }

    @Test
    void testChessGame() throws IOException {

        String whiteId = "Player White";
        String whiteTimeline = simulator.createTimeline(whiteId);

        String blackId = "Player Black";
        String blackTimeline = simulator.createTimeline(blackId);

        String assistantId = "Assistant";
        String assistantTimeline = simulator.createTimeline(assistantId);

        ChessAssisted contract = new ChessAssisted(whiteTimeline, blackTimeline, assistantTimeline);
        InitiateContractAction initiateContractAction = new InitiateContractAction(contract);
        String initiateContractEntry = simulator.appendEntry(whiteTimeline, initiateContractAction);

        String runner = "Contract Runner";
        String runnerTimeline = simulator.createTimeline(runner);

        InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(initiateContractEntry, contract);
        String initiateContractProcessingEntry = simulator.appendEntry(runnerTimeline, initiateProcessingAction);

        ContractRunner2 contractRunner = new ContractRunner2(blue, initiateContractEntry, initiateContractProcessingEntry);
        contractRunner.startProcessingContract(contract, runnerTimeline, simulator);

        simulator.appendEntry(assistantTimeline, initiateContractEntry, move("e2", "e4"));
        simulator.appendEntry(blackTimeline, initiateContractEntry, move("e7", "e5"));
        simulator.appendEntry(assistantTimeline, initiateContractEntry, move("f1", "c4"));
        simulator.appendEntry(blackTimeline, initiateContractEntry, move("a7", "a6"));
        simulator.appendEntry(assistantTimeline, initiateContractEntry, move("d1", "h5"));
        simulator.appendEntry(blackTimeline, initiateContractEntry, move("a6", "a5"));
        simulator.appendEntry(assistantTimeline, initiateContractEntry, move("h5", "f7"));

        simulator.save(runnerTimeline, 2, "src/test/resources", "chess");

        ContractUpdateAction action = simulator.getMessageFromLastTimelineEntry(runnerTimeline, ContractUpdateAction.class);
        Chess finalAfterMoves = blue.convertObject(action.getContractInstance().getContractState(), Chess.class);
        assertEquals("rnbqkbnr/1ppp1Qpp/8/p3p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4", finalAfterMoves.getProperties().getChessboard());

    }

    private Node move(String from, String to) {
        return blue.objectToNode(new ChessMove(from, to));
    }

}