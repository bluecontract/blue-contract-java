package blue.contract.simulator;

import blue.contract.model.chess.Chess;
import blue.contract.model.chess.ChessMove;
import blue.contract.model.action.InitiateContractAction;
import blue.contract.model.action.InitiateContractProcessingAction;
import blue.contract.utils.RepositoryExportingTool;
import blue.language.Blue;
import blue.language.provider.DirectoryBasedNodeProvider;
import blue.language.utils.TypeClassResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static blue.contract.utils.Utils.defaultTestingEnvironment;
import static blue.contract.utils.Utils.testBlue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulatedContractTest {

    private Simulator simulator;
    private Blue blue;

    @BeforeEach
    void setUp() throws IOException {
        blue = testBlue();
        simulator = new Simulator(blue);
    }

    @Test
    void testDirectTimelineEntries() throws IOException {
        String alice = "Alice";
        String aliceTimeline = simulator.createTimeline(alice);

        String bob = "Bob";
        String bobTimeline = simulator.createTimeline(bob);

        Chess chess = new Chess(aliceTimeline, bobTimeline);
        InitiateContractAction initiateChessAction = new InitiateContractAction(chess);
        String initiateContractEntry = simulator.appendEntry(aliceTimeline, initiateChessAction);

        String runner = "Contract Runner";
        String runnerTimeline = simulator.createTimeline(runner);

        InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(initiateContractEntry, chess);
        String initiateContractProcessingEntry = simulator.appendEntry(runnerTimeline, initiateProcessingAction);

        ContractRunner contractRunner = new ContractRunner(blue, initiateContractEntry, initiateContractProcessingEntry);
        contractRunner.startProcessingContract(chess, runnerTimeline, simulator);

        simulator.appendEntry(aliceTimeline, initiateContractEntry, new ChessMove("e2", "e4"));
        simulator.appendEntry(bobTimeline, initiateContractEntry, new ChessMove("e7", "e5"));
        simulator.appendEntry(aliceTimeline, initiateContractEntry, new ChessMove("f1", "c4"));
        simulator.appendEntry(bobTimeline, initiateContractEntry, new ChessMove("a7", "a6"));
        simulator.appendEntry(aliceTimeline, initiateContractEntry, new ChessMove("d1", "h5"));
        simulator.appendEntry(bobTimeline, initiateContractEntry, new ChessMove("a6", "a5"));
        simulator.appendEntry(aliceTimeline, initiateContractEntry, new ChessMove("h5", "f7"));

        Chess finalAfterMoves = blue.convertObject(contractRunner.getLastContractUpdate().getContractInstance().getContractState(), Chess.class);
        assertEquals("rnbqkbnr/1ppp1Qpp/8/p3p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4", finalAfterMoves.getProperties().getChessboard());

//        contractRunner.save("src/test/resources", "chess");

    }

    @Test
    void testRunningContract() throws IOException {
        String alice = "Alice";
        String aliceTimeline = simulator.createTimeline(alice);

        String bob = "Bob";
        String bobTimeline = simulator.createTimeline(bob);

        Chess chess = new Chess(aliceTimeline, bobTimeline);
        InitiateContractAction initiateChessAction = new InitiateContractAction(chess);
        String initiateContractEntry = simulator.appendEntry(aliceTimeline, initiateChessAction);

        String runner = "Contract Runner";
        String runnerTimeline = simulator.createTimeline(runner);

        InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(initiateContractEntry, chess);
        String initiateContractProcessingEntry = simulator.appendEntry(runnerTimeline, initiateProcessingAction);

        ContractRunner contractRunner = new ContractRunner(blue, initiateContractEntry, initiateContractProcessingEntry);
        contractRunner.startProcessingContract(chess, runnerTimeline, simulator);

        simulator.appendEntry(aliceTimeline, initiateContractEntry, new ChessMove("e2", "e4"));
        simulator.appendEntry(bobTimeline, initiateContractEntry, new ChessMove("e7", "e5"));
        simulator.appendEntry(aliceTimeline, initiateContractEntry, new ChessMove("f1", "c4"));
        simulator.appendEntry(bobTimeline, initiateContractEntry, new ChessMove("a7", "a6"));
        simulator.appendEntry(aliceTimeline, initiateContractEntry, new ChessMove("d1", "h5"));
        simulator.appendEntry(bobTimeline, initiateContractEntry, new ChessMove("a6", "a5"));
        simulator.appendEntry(aliceTimeline, initiateContractEntry, new ChessMove("h5", "f7"));

        Chess finalAfterMoves = blue.convertObject(contractRunner.getLastContractUpdate().getContractInstance().getContractState(), Chess.class);
        assertEquals("rnbqkbnr/1ppp1Qpp/8/p3p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4", finalAfterMoves.getProperties().getChessboard());

//        contractRunner.save("src/test/resources", "chess");

    }

}