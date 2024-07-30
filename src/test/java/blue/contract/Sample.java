package blue.contract;

import blue.language.Blue;
import blue.language.NodeProvider;
import blue.language.model.Node;
import blue.language.provider.DirectoryBasedNodeProvider;
import blue.language.provider.ipfs.IPFSNodeProvider;
import blue.language.utils.SequentialNodeProvider;
import blue.language.utils.TypeClassResolver;

import java.io.File;
import java.io.IOException;

import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class Sample {

    public static void main(String[] args) throws IOException {
        runTwoContracts();
    }

    private static void runSingleContract() throws IOException {
        Node contract = YAML_MAPPER.readValue(new File("src/test/resources/contract6.blue"), Node.class);
        Node event = YAML_MAPPER.readValue(new File("src/test/resources/event.blue"), Node.class);
        String initiateContractEntryBlueId = "6fauav11TexaBmxXWURBbwLjXnsLgvEZX9QKyajeSrKR";
        String initiateContractProcessingEntryBlueId = "BeTSqC2nC2jmUNSKJJQxrNzUcVc2P674Bi637bsBTy1";

        Blue blue = defaultBlue();
        ContractSimulator simulator = new ContractSimulator(blue, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId, "c6");

        simulator.initiateContract(contract);
        simulator.processEmittedEventsOnly();

        simulator.addEvent(event);
        simulator.processEvents(5);

        Node successfulPaymentEvent = new Node().type(new Node().blueId("6qFN7V1kCXU2CVvnNrVJMdoi9nUCoBnwL4Q5B6FQP4x1"));
        simulator.addEvent(successfulPaymentEvent);

        simulator.processEvents(25);
    }

    private static void runTwoContracts() throws IOException {
        Node contract1 = YAML_MAPPER.readValue(new File("src/test/resources/contract6.blue"), Node.class);
        Node contract2 = YAML_MAPPER.readValue(new File("src/test/resources/contract6b.blue"), Node.class);
        String initiateContractEntryBlueId1 = "6fauav11TexaBmxXWURBbwLjXnsLgvEZX9QKyajeSrKR";
        String initiateContractProcessingEntryBlueId1 = "BeTSqC2nC2jmUNSKJJQxrNzUcVc2P674Bi637bsBTy1";
        String initiateContractEntryBlueId2 = "RBbwLjXnsLgvEZX9QKyajeSrKR6fauav11TexaBmxXWU";
        String initiateContractProcessingEntryBlueId2 = "xrNzUcVc2P674Bi637bsBTy1BeTSqC2nC2jmUNSKJJQ";

        Blue blue = defaultBlue();
        ContractSimulator simulator1 = new ContractSimulator(blue, initiateContractEntryBlueId1, initiateContractProcessingEntryBlueId1, "c61");
        ContractSimulator simulator2 = new ContractSimulator(blue, initiateContractEntryBlueId2, initiateContractProcessingEntryBlueId2, "c62");

        simulator1.initiateContract(contract1);
        simulator2.initiateContract(contract2);

        for (Node generatedEvent : simulator1.getPendingEvents()) {
            simulator2.addEvent(generatedEvent);
        }
        simulator2.processEvents(3);

    }

    private static Blue defaultBlue() {
        NodeProvider directoryBasedNodeProvider = null;
        try {
            directoryBasedNodeProvider = new DirectoryBasedNodeProvider("types", "samples");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new Blue(
                new SequentialNodeProvider(
                        directoryBasedNodeProvider,
                        new IPFSNodeProvider()
                ),
                new TypeClassResolver("blue.contract.model")
        );
    }

}
