package blue.contract.simulator;

import blue.contract.ContractProcessor;
import blue.contract.model.Contract;
import blue.contract.model.ContractInstance;
import blue.contract.model.ContractUpdateAction;
import blue.contract.processor.StandardProcessorsProvider;
import blue.contract.simulator.utils.ContractRunnerSubscriptionUtils;
import blue.contract.simulator.model.SimulatorTimelineEntry;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.NodeToMapListOrValue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class ContractRunnerMT {

    private final Blue blue;
    private final ContractProcessor contractProcessor;
    private final String initiateContractEntryBlueId;
    private final String initiateContractProcessingEntryBlueId;
    private List<ContractUpdateAction> contractUpdateActions = new CopyOnWriteArrayList<>();
    private SimulatorMT simulator;
    private volatile boolean isRunning = true;
    private final BlockingQueue<SimulatorTimelineEntry<Object>> eventQueue;
    private Thread processingThread;
    private String runnerTimeline;

    public ContractRunnerMT(Blue blue, String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        System.out.println("Initializing ContractRunnerMT");
        StandardProcessorsProvider provider = new StandardProcessorsProvider(blue);
        this.blue = blue;
        this.contractProcessor = new ContractProcessor(provider, blue);
        this.initiateContractEntryBlueId = initiateContractEntryBlueId;
        this.initiateContractProcessingEntryBlueId = initiateContractProcessingEntryBlueId;
        this.eventQueue = new LinkedBlockingQueue<>();
        this.processingThread = new Thread(this::processEvents);
        this.processingThread.start();
        System.out.println("ContractRunnerMT initialized with initiateContractEntryBlueId: " + initiateContractEntryBlueId +
                           " and initiateContractProcessingEntryBlueId: " + initiateContractProcessingEntryBlueId);
    }

    public List<ContractUpdateAction> initiateContract(Object contract) {
        System.out.println("Initiating contract from Object");
        return initiateContract(blue.objectToNode(contract));
    }

    public List<ContractUpdateAction> initiateContract(Node contract) {
        System.out.println("Initiating contract from Node");
        List<ContractUpdateAction> actions = contractProcessor.initiate(contract, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
        System.out.println("Contract initiated. Number of update actions: " + actions.size());
        return actions;
    }

    public List<ContractUpdateAction> getContractUpdates() {
        System.out.println("Retrieving contract updates. Current count: " + contractUpdateActions.size());
        return new ArrayList<>(contractUpdateActions);
    }

    public ContractUpdateAction getLastContractUpdate() {
        if (contractUpdateActions.isEmpty()) {
            System.out.println("No contract updates available");
            return null;
        }
        System.out.println("Retrieving last contract update");
        return contractUpdateActions.get(contractUpdateActions.size() - 1);
    }

    public void startProcessingContract(Contract contract, String runnerTimeline, SimulatorMT simulator) {
        this.simulator = simulator;
        this.runnerTimeline = runnerTimeline;

        System.out.println("Setting up subscription for contract events");
        simulator.subscribe(
                entry -> ContractRunnerSubscriptionUtils.createContractFilter(contract, initiateContractEntryBlueId).test(entry),
                entry -> {
                    try {
                        eventQueue.put(entry);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
        );

        System.out.println("Starting to process contract on runnerTimeline: " + runnerTimeline);
        List<ContractUpdateAction> actions = initiateContract(contract);
        contractUpdateActions.addAll(actions);
        System.out.println("Appending initial contract update actions to simulator");
        actions.forEach(action -> simulator.appendEntry(runnerTimeline, initiateContractProcessingEntryBlueId, action));
    }

    private void processEvents() {
        while (isRunning || !eventQueue.isEmpty()) {
            try {
                SimulatorTimelineEntry<Object> entry = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    processContractEvent(entry);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("ContractRunnerMT event processing stopped");
    }

    private void processContractEvent(SimulatorTimelineEntry<Object> entry) {
        System.out.println("Processing new contract event");
        ContractUpdateAction lastUpdate = getLastContractUpdate();
        int epoch = lastUpdate != null ? lastUpdate.getEpoch() : 0;
        System.out.println("Current epoch: " + epoch);
        List<ContractUpdateAction> result = contractProcessor.processEvent(
                blue.objectToNode(entry),
                lastUpdate != null ? lastUpdate.getContractInstance() : null,
                initiateContractEntryBlueId,
                initiateContractProcessingEntryBlueId,
                epoch + 1
        );
        System.out.println("Event processed. Number of new update actions: " + result.size());
        contractUpdateActions.addAll(result);
        System.out.println("Appending new contract update actions to simulator");
        result.forEach(action -> simulator.appendEntry(runnerTimeline, initiateContractProcessingEntryBlueId, action));
    }

    public void stop() throws InterruptedException {
        isRunning = false;
        processingThread.join(5000); // Wait up to 5 seconds for processing to complete
        System.out.println("ContractRunnerMT stopped");
    }

    private void processContractEvent(SimulatorTimelineEntry<Object> entry, String runnerTimeline) {
        System.out.println("isRunning:" + isRunning);
        if (!isRunning) return;

        CompletableFuture.runAsync(() -> {
            System.out.println("Processing new contract event");
            ContractUpdateAction lastUpdate = getLastContractUpdate();
            int epoch = lastUpdate != null ? lastUpdate.getEpoch() : 0;
            System.out.println("Current epoch: " + epoch);
            List<ContractUpdateAction> result = contractProcessor.processEvent(
                    blue.objectToNode(entry),
                    lastUpdate != null ? lastUpdate.getContractInstance() : null,
                    initiateContractEntryBlueId,
                    initiateContractProcessingEntryBlueId,
                    epoch + 1
            );
            System.out.println("Event processed. Number of new update actions: " + result.size());
            contractUpdateActions.addAll(result);
            System.out.println("Appending new contract update actions to simulator");
            result.forEach(action -> simulator.appendEntry(runnerTimeline, initiateContractProcessingEntryBlueId, action));
        });
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
}