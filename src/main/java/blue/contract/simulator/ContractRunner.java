package blue.contract.simulator;

import blue.contract.SingleEventContractProcessor;
import blue.contract.model.Contract;
import blue.contract.model.ContractInstance;
import blue.contract.model.ContractUpdateAction;
import blue.contract.processor.StandardProcessorsProvider;
import blue.contract.model.blink.SimulatorTimelineEntry;
import blue.contract.simulator.utils.ContractRunnerSubscriptionUtils;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.BlueIds;
import blue.language.utils.NodeToMapListOrValue;
import blue.language.utils.limits.TypeSpecificPropertyFilter;
import org.gradle.internal.impldep.com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class ContractRunner {

    private final Blue blue;
    private final SingleEventContractProcessor singleEventContractProcessor;
    private final String initiateContractEntryBlueId;
    private final String initiateContractProcessingEntryBlueId;
    private final List<Node> pendingEvents;
    private ContractInstance currentInstance;
    private List<Node> processedEvents = new ArrayList<>();
    private List<ContractUpdateAction> contractUpdateActions = new ArrayList<>();
    private Simulator simulator;

    public ContractRunner(Blue blue, String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        StandardProcessorsProvider provider = new StandardProcessorsProvider(blue);
        this.blue = blue;
        blue.setGlobalLimits(new TypeSpecificPropertyFilter(
                BlueIds.getBlueId(SimulatorTimelineEntry.class).orElseThrow(() -> new RuntimeException("No Simulator Timeline Entry blueId found.")),
                ImmutableSet.of("timeline", "timelinePrev", "thread", "threadPrev", "signature")));
        this.singleEventContractProcessor = new SingleEventContractProcessor(provider, blue);
        // don't verify timeline related fields
//        singleEventContractProcessor.getContractProcessorConfig().set(EVENT_TARGET_TYPE_TRANSFORMER,
//                (NodeTypeMatcher.TargetTypeTransformer) targetType -> {
//                    if (targetType.getProperties() != null) {
//                        Node result = targetType.clone();
//                        result.getProperties().remove("timeline");
//                        result.getProperties().remove("timelinePrev");
//                        result.getProperties().remove("thread");
//                        result.getProperties().remove("threadPrev");
//                        result.getProperties().remove("signature");
//                        return result;
//                    }
//                    return targetType;
//                }
//        );
        this.initiateContractEntryBlueId = initiateContractEntryBlueId;
        this.initiateContractProcessingEntryBlueId = initiateContractProcessingEntryBlueId;
        this.pendingEvents = new ArrayList<>();
    }

    public ContractUpdateAction initiateContract(Object contract) {
        return initiateContract(blue.objectToNode(contract));
    }

    public ContractUpdateAction initiateContract(Node contract) {
        ContractUpdateAction update = singleEventContractProcessor.initiate(contract, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
        contractUpdateActions.add(update);
        currentInstance = update.getContractInstance();
        if (update.getEmittedEvents() != null) {
            pendingEvents.addAll(update.getEmittedEvents());
        }
        return update;
    }

    public void addEvent(Node event) {
        pendingEvents.add(event);
    }

    public void addEvent(Object event) {
        addEvent(blue.objectToNode(blue.clone(event)));
    }

    public void processEvents() {
        processEvents(pendingEvents.size());
    }

    public void processEvents(int numberOfEventsToProcess) {
        int eventsProcessed = 0;
        while (!pendingEvents.isEmpty() && eventsProcessed < numberOfEventsToProcess) {
            Node event = pendingEvents.remove(0);
            ContractUpdateAction update = singleEventContractProcessor.processEvent(event, currentInstance,
                    initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
            contractUpdateActions.add(update);
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
                ContractUpdateAction update = singleEventContractProcessor.processEvent(event, currentInstance,
                        initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
                contractUpdateActions.add(update);
                currentInstance = update.getContractInstance();
                if (update.getEmittedEvents() != null) {
                    pendingEvents.addAll(update.getEmittedEvents());
                }
            }
        }
    }

    public void save(String directory, String filePrefix) throws IOException {
        for (int i = 0; i < contractUpdateActions.size(); i++) {
            ContractUpdateAction contractUpdateAction = contractUpdateActions.get(i);
            Node contractUpdateNode = JSON_MAPPER.convertValue(contractUpdateAction, Node.class);

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
        return contractUpdateActions.size();
    }

    public List<ContractUpdateAction> getContractUpdates() {
        return contractUpdateActions;
    }

    public ContractUpdateAction getLastContractUpdate() {
        return contractUpdateActions.isEmpty() ? null : contractUpdateActions.get(contractUpdateActions.size() - 1);
    }

    public void startProcessingContract(Contract contract, String runnerTimeline, Simulator simulator) {
        this.simulator = simulator;
        ContractUpdateAction initiateAction = initiateContract(contract);
        initiateAction.epoch(0);
        simulator.appendEntry(runnerTimeline, initiateContractProcessingEntryBlueId, initiateAction);
        simulator.subscribe(
                ContractRunnerSubscriptionUtils.createContractFilter(contract, initiateContractEntryBlueId, runnerTimeline, simulator),
                entry -> {
                    int epoch = getLastContractUpdate().getEpoch();
                    addEvent(entry);
                    processEvents();
                    ContractUpdateAction result = getLastContractUpdate();
                    result.epoch(epoch + 1);
                    simulator.appendEntry(runnerTimeline, initiateContractProcessingEntryBlueId, result);
                });
    }
}