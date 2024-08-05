package blue.contract;

import blue.contract.model.ContractUpdate;
import blue.contract.model.TimelineEntry;
import blue.contract.model.event.ContractProcessingEvent;
import blue.contract.model.event.ContractUpdateEvent;
import blue.contract.packager.model.BluePackage;
import blue.contract.utils.ContractSimulator;
import blue.contract.utils.PackagingUtils.ClasspathBasedPackagingEnvironment;
import blue.contract.utils.SampleBlueIds;
import blue.language.Blue;
import blue.language.NodeProvider;
import blue.language.model.Node;
import blue.language.provider.ClasspathBasedNodeProvider;
import blue.language.utils.BlueIds;
import blue.language.utils.NodeToObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static blue.contract.utils.PackagingUtils.createClasspathBasedPackagingEnvironment;
import static blue.contract.utils.Properties.BLUE_CONTRACTS_V04;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_1;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_2;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChessTest {

    private static ClasspathBasedPackagingEnvironment env;
    private static Blue blue;
    private static BluePackage contractPackage;

    @BeforeAll
    static void setUp() throws IOException {
        List<NodeProvider> additionalNodeProviders = Collections.singletonList(new ClasspathBasedNodeProvider("samples"));
        env = createClasspathBasedPackagingEnvironment("repository", "blue.contract.model", additionalNodeProviders);
        blue = env.getBlue();
        contractPackage = env.getPackage("Chess");
    }

    @Test
    void testInitializationEvent() throws IOException {
        String timelineWhite = "Hvi3cK5LBVYzgkydR23mPs5ARWYKjEsFd5mcJfGvKxcE";
        String timelineBlack = "ARWYKjEsFd5mcJfGvKxcEHvi3cK5LBVYzgkydR23mPs5";
        Node contract = contractPackage.getPreprocessedNodes().get("Chess");

        ContractSimulator simulator = new ContractSimulator(blue, SAMPLE_BLUE_ID_1, SAMPLE_BLUE_ID_2);

        simulator.initiateContract(contract);

        simulator.addEvent(entry(timelineWhite, move("e2", "e4")));
        simulator.addEvent(entry(timelineWhite, move("e2", "e4")));
        simulator.addEvent(entry(timelineBlack, move("e7", "e5")));
        simulator.addEvent(entry(timelineWhite, move("f1", "c4")));
        simulator.addEvent(entry(timelineBlack, move("a7", "a6")));
        simulator.addEvent(entry(timelineWhite, move("d1", "h5")));
        simulator.addEvent(entry(timelineBlack, move("a6", "a5")));
        simulator.addEvent(entry(timelineWhite, move("h5", "f7")));
        simulator.processEvents(10);

        simulator.save("src/test/resources", "chess");
    }

    private Node entry(String timeline, Node node) {
        BluePackage blueContracts = env.getPackage("Blue Contracts v0.4");
        String move1 = "type:\n" +
                       "  blueId: " + blueContracts.getPreprocessedNodes().get("Timeline Entry").getAsText("/blueId") + "\n" +
                       "timeline:\n" +
                       "  value: " + timeline + "\n" +
                       "  type: Text";
        Node result = blue.preprocess(blue.yamlToNode(move1));
        result.properties("message", node);
        return result;
    }

    private Node move(String from, String to) {
        String move1 = "type:\n" +
                       "  blueId: " + contractPackage.getPreprocessedNodes().get("Chess Move").getAsText("/blueId") + "\n" +
                       "from:\n" +
                       "  value: " + from + "\n" +
                       "  type: Text\n" +
                       "to:\n" +
                       "  value: " + to + "\n" +
                       "  type: Text";
        return blue.preprocess(blue.yamlToNode(move1));
    }

}