package blue.contract;

import blue.contract.utils.ContractSimulator;
import blue.language.Blue;
import blue.language.NodeProvider;
import blue.language.model.Node;
import blue.language.preprocess.Preprocessor;
import blue.language.provider.DirectoryBasedNodeProvider;
import blue.language.provider.SequentialNodeProvider;
import blue.language.provider.ipfs.IPFSNodeProvider;
import blue.language.utils.TypeClassResolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class Sample {

    public static void main(String[] args) throws IOException {
        runSingleContract();
    }

    private static void runSingleContract() throws IOException {
        Node contract = YAML_MAPPER.readValue(new File("src/test/resources/contract9.blue"), Node.class);
        Node event = YAML_MAPPER.readValue(new File("src/test/resources/event.blue"), Node.class);
        String initiateContractEntryBlueId = "6fauav11TexaBmxXWURBbwLjXnsLgvEZX9QKyajeSrKR";
        String initiateContractProcessingEntryBlueId = "BeTSqC2nC2jmUNSKJJQxrNzUcVc2P674Bi637bsBTy1";

        Blue blue = defaultBlue();
        contract = blue.preprocess(contract);
        event = blue.preprocess(event);

        System.out.println(YAML_MAPPER.writeValueAsString(event));

//        ContractSimulator simulator = new ContractSimulator(blue, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
//
//        simulator.initiateContract(contract);
//        simulator.processEmittedEventsOnly();
//
//        simulator.addEvent(event);
//        simulator.processEvents(5);
//
//        Node successfulPaymentEvent = new Node().type(new Node().blueId("6qFN7V1kCXU2CVvnNrVJMdoi9nUCoBnwL4Q5B6FQP4x1"));
//        simulator.addEvent(successfulPaymentEvent);
//
//        simulator.processEvents(25);


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

    public static Blue defaultBlue() {
        NodeProvider directoryBasedNodeProvider = null;
        try {
            Preprocessor preprocessor = new Preprocessor();
            Node blueNode = getBlueForBlueContract();
            Function<Node, Node> preprocessingFunction = doc -> preprocessor.preprocess(doc, blueNode);
            directoryBasedNodeProvider = new DirectoryBasedNodeProvider(preprocessingFunction, "types", "samples");
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