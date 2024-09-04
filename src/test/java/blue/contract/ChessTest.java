package blue.contract;

import blue.contract.model.TimelineEntry;
import blue.contract.model.chess.Chess;
import blue.contract.model.chess.ChessMove;
import blue.contract.simulator.ContractRunner;
import blue.language.Blue;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_1;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_2;
import static blue.contract.utils.Utils.testBlue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChessTest {

    @Test
    void testChessGame() throws IOException {
        Blue blue = testBlue();
        String timelineWhite = "Hvi3cK5LBVYzgkydR23mPs5ARWYKjEsFd5mcJfGvKxcE";
        String timelineBlack = "ARWYKjEsFd5mcJfGvKxcEHvi3cK5LBVYzgkydR23mPs5";
        Chess chess = new Chess(timelineWhite, timelineBlack);

        ContractRunner simulator = new ContractRunner(blue, SAMPLE_BLUE_ID_1, SAMPLE_BLUE_ID_2);
        simulator.initiateContract(chess);

        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineWhite).message(new ChessMove("e2", "e4")));
        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineBlack).message(new ChessMove("e7", "e5")));
        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineWhite).message(new ChessMove("f1", "c4")));
        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineBlack).message(new ChessMove("a7", "a6")));
        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineWhite).message(new ChessMove("d1", "h5")));
        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineBlack).message(new ChessMove("a6", "a5")));
        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineWhite).message(new ChessMove("h5", "f7")));
        simulator.processEvents();

        Chess finalAfterMoves = blue.convertObject(simulator.getLastContractUpdate().getContractInstance().getContractState(), Chess.class);
        assertEquals("rnbqkbnr/1ppp1Qpp/8/p3p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4", finalAfterMoves.getProperties().getChessboard());

        simulator.save("src/test/resources", "chess");

    }

}