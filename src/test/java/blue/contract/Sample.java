package blue.contract;

import blue.contract.utils.ContractSimulator;
import blue.contract.utils.PackagingUtils;
import blue.language.Blue;
import blue.language.NodeProvider;
import blue.language.model.Node;
import blue.language.preprocess.Preprocessor;
import blue.language.provider.ClasspathBasedNodeProvider;
import blue.language.provider.DirectoryBasedNodeProvider;
import blue.language.provider.SequentialNodeProvider;
import blue.language.provider.ipfs.IPFSNodeProvider;
import blue.language.utils.NodeToObject;
import blue.language.utils.TypeClassResolver;
import blue.contract.utils.PackagingUtils.ClasspathBasedPackagingEnvironment;
import blue.contract.model.ContractUpdate;
import blue.contract.packager.model.BluePackage;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_1;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;
import java.util.Collections;
import java.util.List;

import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;
import static blue.contract.utils.PackagingUtils.createClasspathBasedPackagingEnvironment;

public class Sample {

    private static ClasspathBasedPackagingEnvironment env;
    private static Blue blue;
    private static BluePackage contractPackage;

    public static void main(String[] args) throws IOException {
        runSingleContract();
    }

    private static void runSingleContract() throws IOException {
        Node contract = YAML_MAPPER.readValue(new File("src/test/resources/chess.blue"), Node.class);
        // Node event = YAML_MAPPER.readValue(new File("src/test/resources/event.blue"), Node.class);
        Node event = YAML_MAPPER.readValue(new File("src/test/resources/chess_move_event_manual.blue"), Node.class);
        
        Object obj = NodeToObject.get(
            YAML_MAPPER.readValue(new File("src/test/resources/compare/update_api.blue"), Node.class),
            NodeToObject.Strategy.SIMPLE
        );
        ContractUpdate update = YAML_MAPPER.convertValue(obj, ContractUpdate.class);

        System.out.println("Contract state received from the engine:");
        System.out.println(JSON_MAPPER.writeValueAsString(update.getContractInstance().getContractState()));

        String initiateContractEntryBlueId = "6fauav11TexaBmxXWURBbwLjXnsLgvEZX9QKyajeSrKR";
        String initiateContractProcessingEntryBlueId = "BeTSqC2nC2jmUNSKJJQxrNzUcVc2P674Bi637bsBTy1";

        env = defaulEnv();
        //blue = env.getBlue();
        blue = defaultBlue();
        String timelineWhite = "Hvi3cK5LBVYzgkydR23mPs5ARWYKjEsFd5mcJfGvKxcE";
        String timelineBlack = "ARWYKjEsFd5mcJfGvKxcEHvi3cK5LBVYzgkydR23mPs5";

        //contractPackage = env.getPackage("Chess");
        //contract = contractPackage.getPreprocessedNodes().get("Chess");
        // contract = blue.preprocess(contract);

        ContractSimulator simulator = new ContractSimulator(
            blue,
            SAMPLE_BLUE_ID_1,
            SAMPLE_BLUE_ID_2
        );

        //simulator.setContractInstance(update.getContractInstance());

        simulator.initiateContract(contract);

        // simulator.processEmittedEventsOnly();

        //System.out.println(blue.preprocess(event));
        // System.out.println("---");
        // event = entry(timelineWhite, move("e2", "e4"));
        // System.out.println(event);
        // YAML_MAPPER.writeValue(
        //     new java.io.File("src/test/resources/chess_move_event_simple.blue"),
        //     NodeToObject.get(event, NodeToObject.Strategy.SIMPLE)
        // );
        // YAML_MAPPER.writeValue(
        //     new java.io.File("src/test/resources/chess_move_event.blue"),
        //     NodeToObject.get(event)
        // );
        simulator.addEvent(event);

        
        //simulator.addEvent(entry(timelineWhite, move("e2", "e4")));
        //simulator.addEvent(entry(timelineBlack, move("e7", "e5")));
        // simulator.addEvent(entry(timelineWhite, move("f1", "c4")));
        // simulator.addEvent(entry(timelineBlack, move("a7", "a6")));
        // simulator.addEvent(entry(timelineWhite, move("d1", "h5")));
        // simulator.addEvent(entry(timelineBlack, move("a6", "a5")));
        // simulator.addEvent(entry(timelineWhite, move("h5", "f7")));
        simulator.processEvents(10);

        // Node successfulPaymentEvent = new Node().type(new Node().blueId("6qFN7V1kCXU2CVvnNrVJMdoi9nUCoBnwL4Q5B6FQP4x1"));
        // simulator.addEvent(successfulPaymentEvent);

        // simulator.processEvents(25);

        simulator.save("src/test/resources/simplechess", "simplechess");


    }

    private static Node entry(String timeline, Node node) {
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

    private static Node move(String from, String to) {
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

    private static void runTwoContracts() throws IOException {
        Node contract1 = YAML_MAPPER.readValue(new File("src/test/resources/contract6.blue"), Node.class);
        Node contract2 = YAML_MAPPER.readValue(new File("src/test/resources/contract6b.blue"), Node.class);
        String initiateContractEntryBlueId1 = "6fauav11TexaBmxXWURBbwLjXnsLgvEZX9QKyajeSrKR";
        String initiateContractProcessingEntryBlueId1 = "BeTSqC2nC2jmUNSKJJQxrNzUcVc2P674Bi637bsBTy1";
        String initiateContractEntryBlueId2 = "RBbwLjXnsLgvEZX9QKyajeSrKR6fauav11TexaBmxXWU";
        String initiateContractProcessingEntryBlueId2 = "xrNzUcVc2P674Bi637bsBTy1BeTSqC2nC2jmUNSKJJQ";

        Blue blue = defaultBlue();
        contract1 = blue.preprocess(contract1);
        contract2 = blue.preprocess(contract2);

        ContractSimulator simulator1 = new ContractSimulator(blue, initiateContractEntryBlueId1, initiateContractProcessingEntryBlueId1);
        ContractSimulator simulator2 = new ContractSimulator(blue, initiateContractEntryBlueId2, initiateContractProcessingEntryBlueId2);

        simulator1.initiateContract(contract1);
        simulator2.initiateContract(contract2);

        for (Node generatedEvent : simulator1.getPendingEvents()) {
            simulator2.addEvent(generatedEvent);
        }
        simulator2.processEvents(3);

    }

    public static Blue defaultBlue2() {
        NodeProvider nodeProvider = null;
        try {
            nodeProvider = new SequentialNodeProvider(
                    new ClasspathBasedNodeProvider("samples"),
                    new IPFSNodeProvider()
            );
            PackagingUtils.ClasspathBasedPackagingEnvironment env = PackagingUtils.createClasspathBasedPackagingEnvironment(
                "repository",
                "blue.contract.model",
                Collections.singletonList(nodeProvider)
            );
            return env.getBlue();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Blue defaultBlue1() {
        NodeProvider directoryBasedNodeProvider = null;
        try {
            Preprocessor preprocessor = new Preprocessor();
            Node blueNode = getBlueForBlueContract();
            Function<Node, Node> preprocessingFunction = doc -> preprocessor.preprocess(doc, blueNode);
            directoryBasedNodeProvider = new ClasspathBasedNodeProvider(preprocessingFunction, "samples");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Blue blue = new Blue(
                new SequentialNodeProvider(
                        directoryBasedNodeProvider,
                        new IPFSNodeProvider()
                ),
                new TypeClassResolver("blue.contract.model")
        );
        blue.addPreprocessingAliases(AliasRegistry.MAP);
        return blue;
    }

    public static Blue defaultBlue() throws IOException {
        List<NodeProvider> additionalNodeProviders = Collections.singletonList(new ClasspathBasedNodeProvider("samples"));
        return createClasspathBasedPackagingEnvironment("repository", "blue.contract.model", additionalNodeProviders).getBlue();
    }

    public static ClasspathBasedPackagingEnvironment defaulEnv() throws IOException {
        List<NodeProvider> additionalNodeProviders = Collections.singletonList(new ClasspathBasedNodeProvider("samples"));
        return createClasspathBasedPackagingEnvironment("repository", "blue.contract.model", additionalNodeProviders);
    }

    private static Node getBlueForBlueContract() {
        try (InputStream inputStream = Sample.class.getClassLoader().getResourceAsStream("types/BlueContractSample.blue")) {
            if (inputStream == null) {
                throw new RuntimeException("Unable to find DefaultBlue.blue in classpath");
            }
            return YAML_MAPPER.readValue(inputStream, Node.class);
        } catch (IOException e) {
            throw new RuntimeException("Error loading BlueContract.blue from classpath", e);
        }
    }

}