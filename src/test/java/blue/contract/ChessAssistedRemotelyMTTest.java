package blue.contract;

import blue.contract.model.action.InitiateContractAction;
import blue.contract.model.action.InitiateContractProcessingAction;
import blue.contract.model.blink.Task;
import blue.contract.model.chess.ChessAssistedRemotely;
import blue.contract.model.chess.ChessMove;
import blue.contract.model.chess.MakeChessMoveTask;
import blue.contract.simulator.ContractRunner2;
import blue.contract.simulator.ContractRunnerMT;
import blue.contract.simulator.Simulator;
import blue.contract.simulator.SimulatorMT;
import blue.language.Blue;
import blue.language.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static blue.contract.utils.Utils.testBlue;

public class ChessAssistedRemotelyMTTest {

    private SimulatorMT simulator;
    private Blue blue;

    @BeforeEach
    void setUp() throws IOException {
        blue = testBlue();
        simulator = new SimulatorMT(blue);
    }

    @Test
    void testChessGame() throws IOException, InterruptedException {

        String whiteId = "Player White";
        String whiteTimeline = simulator.createTimeline(whiteId);

        String blackId = "Player Black";
        String blackTimeline = simulator.createTimeline(blackId);

        String assistantId = "Assistant";
        String assistantTimeline = simulator.createTimeline(assistantId);

        String taskRunnerId = "Task Contract Runner";
        String taskRunnerTimeline = simulator.createTimeline(taskRunnerId);

        String chessRunnerId = "Chess Contract Runner";
        String chessRunnerTimeline = simulator.createTimeline(chessRunnerId);

        // Starting task
        Task taskContract = new Task(whiteTimeline, assistantTimeline);
        InitiateContractAction taskInitiateContractAction = new InitiateContractAction(taskContract);
        String taskInitiateContractEntry = simulator.appendEntry(whiteTimeline, taskInitiateContractAction);

        InitiateContractProcessingAction taskInitiateProcessingAction = new InitiateContractProcessingAction(taskInitiateContractEntry, taskContract);
        String taskInitiateContractProcessingEntry = simulator.appendEntry(taskRunnerTimeline, taskInitiateProcessingAction);

        ContractRunnerMT taskRunner = new ContractRunnerMT(blue, taskInitiateContractEntry, taskInitiateContractProcessingEntry);
        taskRunner.startProcessingContract(taskContract, taskRunnerTimeline, simulator);

        // Starting chess
        ChessAssistedRemotely chessContract = new ChessAssistedRemotely(whiteTimeline, blackTimeline, taskInitiateContractEntry);
        InitiateContractAction chessInitiateContractAction = new InitiateContractAction(chessContract);
        String chessInitiateContractEntry = simulator.appendEntry(whiteTimeline, chessInitiateContractAction);

        InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(chessInitiateContractEntry, chessContract);
        String chessInitiateContractProcessingEntry = simulator.appendEntry(chessRunnerTimeline, initiateProcessingAction);

        ContractRunnerMT chessRunner = new ContractRunnerMT(blue, chessInitiateContractEntry, chessInitiateContractProcessingEntry);
        chessRunner.startProcessingContract(chessContract, chessRunnerTimeline, simulator);

//        simulator.appendEntry(whiteTimeline, chessInitiateContractEntry, move("e2", "e4"));
//        Thread.sleep(250);
//        simulator.appendEntry(blackTimeline, chessInitiateContractEntry, move("e7", "e5"));
//        Thread.sleep(250);
//        simulator.appendEntry(whiteTimeline, taskInitiateContractEntry, remoteMove("f1", "c4"));

        // Starting assistance
        simulator.appendEntry(whiteTimeline, taskInitiateContractEntry, remoteMove("e2", "e4"));
        Thread.sleep(500);
//        simulator.appendEntry(blackTimeline, chessInitiateContractEntry, move("e7", "e5"));
//        Thread.sleep(500);
//        simulator.appendEntry(whiteTimeline, taskInitiateContractEntry, remoteMove("f1", "c4"));
//        Thread.sleep(500);
//        simulator.appendEntry(blackTimeline, chessInitiateContractEntry, move("a7", "a6"));
//        Thread.sleep(500);
//        simulator.appendEntry(whiteTimeline, taskInitiateContractEntry, remoteMove("d1", "h5"));
//        simulator.appendEntry(whiteTimeline, chessInitiateContractEntry, move("d1", "h5"));
//        Thread.sleep(500);
//        simulator.appendEntry(blackTimeline, chessInitiateContractEntry, move("a6", "a5"));
//        Thread.sleep(500);
//        simulator.appendEntry(whiteTimeline, taskInitiateContractEntry, remoteMove("h5", "f7"));
//
//        ContractUpdateAction action = simulator.getMessageFromLastTimelineEntry(chessRunnerTimeline, ContractUpdateAction.class);
//        Chess finalAfterMoves = blue.convertObject(action.getContractInstance().getContractState(), Chess.class);
//        assertEquals("rnbqkbnr/1ppp1Qpp/8/p3p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4", finalAfterMoves.getProperties().getChessboard());

        Thread.sleep(4000);
        taskRunner.stop();
        chessRunner.stop();
        simulator.shutdown();

        simulator.save(taskRunnerTimeline, 2, "src/test/resources", "chess_task");
        simulator.save(chessRunnerTimeline, 2, "src/test/resources", "chess_chess");

    }

    private Node move(String from, String to) {
        return blue.objectToNode(new ChessMove(from, to));
    }

    private Node remoteMove(String from, String to) {
        MakeChessMoveTask task = new MakeChessMoveTask(from, to);
        return blue.resolve(blue.objectToNode(task));
    }

}