package blue.contract;

import blue.contract.processor.StandardProcessorsProvider;
import blue.contract.model.ContractInstance;
import blue.contract.model.ContractUpdate;
import blue.language.Blue;
import blue.language.NodeProvider;
import blue.language.model.Node;
import blue.language.utils.*;
import blue.language.utils.ipfs.IPFSNodeProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class Sample {

    public static void main(String[] args) throws IOException {
        Node contract = YAML_MAPPER.readValue(new File("src/test/resources/contract4.blue"), Node.class);
        Node event = YAML_MAPPER.readValue(new File("src/test/resources/event.blue"), Node.class);
        List<Node> emittedEvents = new ArrayList<>();
        String initiateContractEntryBlueId = "6fauav11TexaBmxXWURBbwLjXnsLgvEZX9QKyajeSrKR";
        String initiateContractProcessingEntryBlueId = "BeTSqC2nC2jmUNSKJJQxrNzUcVc2P674Bi637bsBTy1";

        Blue blue = defaultBlue();
        StandardProcessorsProvider provider = new StandardProcessorsProvider(blue);
        ContractProcessor contractProcessor = new ContractProcessor(provider, blue);
        ContractUpdate update = contractProcessor.initiate(contract, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
        save(update, 1);
        if (update.getEmittedEvents() != null) {
            emittedEvents.addAll(update.getEmittedEvents());
        }

//        ContractInstance instance = load(1);
//        update = contractProcessor.processEvent(event, instance, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
//        save(update, 2);
//        if (update.getEmittedEvents() != null) {
//            emittedEvents.addAll(update.getEmittedEvents());
//        }

        int fileId = 2;
        while (!emittedEvents.isEmpty()) {
            Node emittedEvent = emittedEvents.remove(0);
            ContractInstance instance = load(fileId - 1);
            update = contractProcessor.processEvent(emittedEvent, instance, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
            save(update, fileId++);
            if (update.getEmittedEvents() != null) {
                emittedEvents.addAll(update.getEmittedEvents());
            }
        }
    }

    private static void save(ContractUpdate contractUpdate, int id) throws IOException {
        File outputFile = new File("src/test/resources/" + id + "_update.blue");
        YAML_MAPPER.writeValue(outputFile, contractUpdate);
        outputFile = new File("src/test/resources/" + id + "_contractInstance.json");
        JSON_MAPPER.writeValue(outputFile, contractUpdate.getContractInstance());
    }

    private static ContractInstance load(int id) throws IOException {
        File inputFile = new File("src/test/resources/" + id + "_contractInstance.json");
        return YAML_MAPPER.readValue(inputFile, ContractInstance.class);
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
                new TypeClassResolver( "blue.contract.model")
        );
    }

}
