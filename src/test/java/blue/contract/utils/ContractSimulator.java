package blue.contract.utils;

import blue.contract.ContractProcessor;
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
    private ContractInstance currentInstance;
    private List<Node> processedEvents = new ArrayList<>();
    private List<ContractUpdate> contractUpdates = new ArrayList<>();

    public ContractSimulator(Blue blue, String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        StandardProcessorsProvider provider = new StandardProcessorsProvider(blue);
        this.contractProcessor = new ContractProcessor(provider, blue);
        this.initiateContractEntryBlueId = initiateContractEntryBlueId;
        this.initiateContractProcessingEntryBlueId = initiateContractProcessingEntryBlueId;
        this.pendingEvents = new ArrayList<>();
    }

    public void initiateContract(Node contract) {
        ContractUpdate update = contractProcessor.initiate(contract, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
        contractUpdates.add(update);
        currentInstance = update.getContractInstance();
        if (update.getEmittedEvents() != null) {
            pendingEvents.addAll(update.getEmittedEvents());
        }
    }

    public void setContractInstance(ContractInstance ci) {
        currentInstance = ci;
    }

    public void addEvent(Node event) {
        pendingEvents.add(event);
    }

    public void processEvents(int numberOfEventsToProcess) {
        int eventsProcessed = 0;
        while (!pendingEvents.isEmpty() && eventsProcessed < numberOfEventsToProcess) {
            Node event = pendingEvents.remove(0);
            ContractUpdate update = contractProcessor.processEvent(event, currentInstance,
                    initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
            contractUpdates.add(update);
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

    public void processEmittedEventsOnly() {
        int initialPendingEventsCount = pendingEvents.size();
        for (int i = 0; i < initialPendingEventsCount; i++) {
            if (!pendingEvents.isEmpty()) {
                Node event = pendingEvents.remove(0);
                ContractUpdate update = contractProcessor.processEvent(event, currentInstance,
                        initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
                contractUpdates.add(update);
                currentInstance = update.getContractInstance();
                if (update.getEmittedEvents() != null) {
                    pendingEvents.addAll(update.getEmittedEvents());
                }
            }
        }
    }

    public void save(String directory, String filePrefix) throws IOException {
        for (int i = 0; i < contractUpdates.size(); i++) {
            ContractUpdate contractUpdate = contractUpdates.get(i);
            Node contractUpdateNode = JSON_MAPPER.convertValue(contractUpdate, Node.class);

            File outputFile = new File(directory + "/" + filePrefix + "_" + (i + 1) + "_update.blue");
            YAML_MAPPER.writeValue(outputFile, NodeToObject.get(contractUpdateNode, NodeToObject.Strategy.SIMPLE));

            Node contractInstanceNode = contractUpdateNode.getAsNode("/contractInstance");
            outputFile = new File(directory + "/" + filePrefix + "_" + (i + 1) + "_contractInstance.json");
            JSON_MAPPER.writeValue(outputFile, NodeToObject.get(contractInstanceNode, NodeToObject.Strategy.SIMPLE));
        }
    }

    public List<Node> getPendingEvents() {
        return new ArrayList<>(pendingEvents);
    }

    public int getCurrentFileId() {
        return contractUpdates.size();
    }

    public List<ContractUpdate> getContractUpdates() {
        return contractUpdates;
    }
}