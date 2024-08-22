package blue.contract;

import blue.contract.model.*;
import blue.contract.utils.ContractSimulator;
import blue.contract.utils.PackagingUtils.ClasspathBasedPackagingEnvironment;
import blue.contract.utils.RepositoryExportingTool;
import blue.language.Blue;
import blue.language.NodeProvider;
import blue.language.model.Node;
import blue.language.provider.ClasspathBasedNodeProvider;
import blue.language.provider.DirectoryBasedNodeProvider;
import blue.language.utils.TypeClassResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static blue.contract.utils.PackagingUtils.createClasspathBasedPackagingEnvironment;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_1;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_2;
import static blue.contract.utils.Utils.defaultTestingEnvironment;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChessTest {

    private static Blue blue;

    @BeforeAll
    static void setUp() throws IOException {
        new RepositoryExportingTool(defaultTestingEnvironment()).exportRepository();
        blue = new Blue(
                new DirectoryBasedNodeProvider("blue-preprocessed", "samples"),
                new TypeClassResolver("blue.contract.model")
        );
    }

    @Test
    void testChessGame() throws IOException {
        String timelineWhite = "Hvi3cK5LBVYzgkydR23mPs5ARWYKjEsFd5mcJfGvKxcE";
        String timelineBlack = "ARWYKjEsFd5mcJfGvKxcEHvi3cK5LBVYzgkydR23mPs5";
        Chess chess = new Chess(timelineWhite, timelineBlack);

        ContractSimulator simulator = new ContractSimulator(blue, SAMPLE_BLUE_ID_1, SAMPLE_BLUE_ID_2);
        simulator.initiateContract(chess);

        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineWhite).message(new ChessMove("e2", "e4")));
        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineBlack).message(new ChessMove("e7", "e5")));
        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineWhite).message(new ChessMove("f1", "c4")));
        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineBlack).message(new ChessMove("a7", "a6")));
        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineWhite).message(new ChessMove("d1", "h5")));
        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineBlack).message(new ChessMove("a6", "a5")));
        simulator.addEvent(new TimelineEntry<ChessMove>().timeline(timelineWhite).message(new ChessMove("h5", "f7")));
        simulator.processEvents();

        Chess finalAfterMoves = blue.nodeToObject(simulator.getLastContractUpdate().getContractInstance().getContractState(), Chess.class);
        assertEquals("rnbqkbnr/1ppp1Qpp/8/p3p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4", finalAfterMoves.getProperties().getChessboard());

//        simulator.save("src/test/resources", "chess");

    }

}