package blue.contract.utils;

import blue.contract.ContractProcessor;
import blue.contract.model.ContractInstance;
import blue.contract.model.ContractUpdate;
import blue.contract.processor.StandardProcessorsProvider;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.NodeToMapListOrValue;
import blue.language.utils.NodeTypeMatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static blue.contract.model.ContractProcessorConfig.EVENT_TARGET_TYPE_TRANSFORMER;
import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class ContractSimulator {

    private final Blue blue;
    private final ContractProcessor contractProcessor;
    private final String initiateContractEntryBlueId;
    private final String initiateContractProcessingEntryBlueId;
    private final List<Node> pendingEvents;
    private ContractInstance currentInstance;
    private List<Node> processedEvents = new ArrayList<>();
    private List<ContractUpdate> contractUpdates = new ArrayList<>();

    public ContractSimulator(Blue blue, String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        StandardProcessorsProvider provider = new StandardProcessorsProvider(blue);
        this.blue = blue;
        this.contractProcessor = new ContractProcessor(provider, blue);
        // don't verify timeline related fields
        contractProcessor.getContractProcessorConfig().set(EVENT_TARGET_TYPE_TRANSFORMER,
                (NodeTypeMatcher.TargetTypeTransformer) targetType -> {
                    if (targetType.getProperties() != null) {
                        Node result = targetType.clone();
                        result.getProperties().remove("timeline");
                        result.getProperties().remove("thread");
                        result.getProperties().remove("signature");
                        return result;
                    }
                    return targetType;
                }
        );
        this.initiateContractEntryBlueId = initiateContractEntryBlueId;
        this.initiateContractProcessingEntryBlueId = initiateContractProcessingEntryBlueId;
        this.pendingEvents = new ArrayList<>();
    }

    public void initiateContract(Object contract) {
        initiateContract(blue.objectToNode(contract));
    }

    public void initiateContract(Node contract) {
        ContractUpdate update = contractProcessor.initiate(contract, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
        contractUpdates.add(update);
        currentInstance = update.getContractInstance();
        if (update.getEmittedEvents() != null) {
            pendingEvents.addAll(update.getEmittedEvents());
        }
    }

    public void addEvent(Node event) {
        pendingEvents.add(event);
    }

    public void addEvent(Object event) {
        addEvent(blue.objectToNode(event));
    }

    public void processEvents() {
        processEvents(pendingEvents.size());
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
            YAML_MAPPER.writeValue(outputFile, NodeToMapListOrValue.get(contractUpdateNode, NodeToMapListOrValue.Strategy.SIMPLE));

            Node contractInstanceNode = contractUpdateNode.getAsNode("/contractInstance");
            outputFile = new File(directory + "/" + filePrefix + "_" + (i + 1) + "_contractInstance.json");
            JSON_MAPPER.writeValue(outputFile, NodeToMapListOrValue.get(contractInstanceNode, NodeToMapListOrValue.Strategy.SIMPLE));
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

    public ContractUpdate getLastContractUpdate() {
        return contractUpdates.isEmpty() ? null : contractUpdates.get(contractUpdates.size() - 1);
    }

}