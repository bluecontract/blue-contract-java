package blue.contract;

import blue.contract.model.ContractInstance;
import blue.contract.model.ContractUpdate;
import blue.contract.processor.StandardProcessorsProvider;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.NodeToObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ContractSimulator {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private final ContractProcessor contractProcessor;
    private final String initiateContractEntryBlueId;
    private final String initiateContractProcessingEntryBlueId;
    private final List<Node> pendingEvents;
    private int currentFileId;
    private ContractInstance currentInstance;
    private List<Node> processedEvents = new ArrayList<>();
    private final String filePrefix;

    public ContractSimulator(Blue blue, String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId, String filePrefix) {
        StandardProcessorsProvider provider = new StandardProcessorsProvider(blue);
        this.contractProcessor = new ContractProcessor(provider, blue);
        this.initiateContractEntryBlueId = initiateContractEntryBlueId;
        this.initiateContractProcessingEntryBlueId = initiateContractProcessingEntryBlueId;
        this.pendingEvents = new ArrayList<>();
        this.currentFileId = 1;
        this.filePrefix = filePrefix;
    }

    public void initiateContract(Node contract) throws IOException {
        ContractUpdate update = contractProcessor.initiate(contract, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
        save(update, currentFileId++);
        currentInstance = update.getContractInstance();
        if (update.getEmittedEvents() != null) {
            pendingEvents.addAll(update.getEmittedEvents());
        }
    }

    public void addEvent(Node event) {
        pendingEvents.add(event);
    }

    public void processEvents(int numberOfEventsToProcess) throws IOException {
        int eventsProcessed = 0;
        while (!pendingEvents.isEmpty() && eventsProcessed < numberOfEventsToProcess) {
            Node event = pendingEvents.remove(0);
            ContractUpdate update = contractProcessor.processEvent(event, currentInstance,
                    initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
            save(update, currentFileId++);
            currentInstance = update.getContractInstance();
            if (update.getEmittedEvents() != null) {
                pendingEvents.addAll(update.getEmittedEvents());
            }
            processedEvents.add(event);
            eventsProcessed++;
        }
    }

    public List<Node> getProcessedEvents() {
        return new ArrayList<>(processedEvents);
    }

    public void processEmittedEventsOnly() throws IOException {
        int initialPendingEventsCount = pendingEvents.size();
        for (int i = 0; i < initialPendingEventsCount; i++) {
            if (!pendingEvents.isEmpty()) {
                Node event = pendingEvents.remove(0);
                ContractUpdate update = contractProcessor.processEvent(event, currentInstance,
                        initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
                save(update, currentFileId++);
                currentInstance = update.getContractInstance();
                if (update.getEmittedEvents() != null) {
                    pendingEvents.addAll(update.getEmittedEvents());
                }
            }
        }
    }

    private void save(ContractUpdate contractUpdate, int id) throws IOException {
        Node contractUpdateNode = JSON_MAPPER.convertValue(contractUpdate, Node.class);
        File outputFile = new File("src/test/resources/" + filePrefix + "_" + id + "_update.blue");
        YAML_MAPPER.writeValue(outputFile, NodeToObject.get(contractUpdateNode, NodeToObject.Strategy.SIMPLE));

        Node contractInstanceNode = contractUpdateNode.getAsNode("/contractInstance");
        outputFile = new File("src/test/resources/" + filePrefix + "_" + id + "_contractInstance.json");
        JSON_MAPPER.writeValue(outputFile, NodeToObject.get(contractInstanceNode, NodeToObject.Strategy.SIMPLE));
    }

    private ContractInstance load(int id) throws IOException {
        File inputFile = new File("src/test/resources/" + filePrefix + "_" + id + "_contractInstance.json");
        return YAML_MAPPER.readValue(inputFile, ContractInstance.class);
    }

    public List<Node> getPendingEvents() {
        return new ArrayList<>(pendingEvents);
    }

    public int getCurrentFileId() {
        return currentFileId;
    }

}