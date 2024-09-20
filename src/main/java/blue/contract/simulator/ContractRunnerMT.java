package blue.contract.simulator;

import blue.contract.ContractProcessor;
import blue.contract.model.Contract;
import blue.contract.model.ContractUpdateAction;
import blue.contract.processor.StandardProcessorsProvider;
import blue.contract.simulator.utils.ContractRunnerSubscriptionUtils;
import blue.contract.model.blink.SimulatorTimelineEntry;
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
    private final BlockingQueue<Node> eventQueue;
    private Thread processingThread;
    private String runnerTimeline;

    public ContractRunnerMT(Blue blue, String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        StandardProcessorsProvider provider = new StandardProcessorsProvider(blue);
        this.blue = blue;
        this.contractProcessor = new ContractProcessor(provider, blue);
        this.initiateContractEntryBlueId = initiateContractEntryBlueId;
        this.initiateContractProcessingEntryBlueId = initiateContractProcessingEntryBlueId;
        this.eventQueue = new LinkedBlockingQueue<>();
        this.processingThread = new Thread(this::processEvents);
        this.processingThread.start();
    }

    public List<ContractUpdateAction> initiateContract(Object contract) {
        return initiateContract(blue.objectToNode(contract));
    }

    public List<ContractUpdateAction> initiateContract(Node contract) {
        return contractProcessor.initiate(contract, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
    }

    public ContractUpdateAction getLastContractUpdate() {
        if (contractUpdateActions.isEmpty()) {
            return null;
        }
        return contractUpdateActions.get(contractUpdateActions.size() - 1);
    }

    public void startProcessingContract(Contract contract, String runnerTimeline, SimulatorMT simulator) {
        this.simulator = simulator;
        this.runnerTimeline = runnerTimeline;

        simulator.subscribe(
                entry -> ContractRunnerSubscriptionUtils.createContractFilterForSimulatorMT(contract, initiateContractEntryBlueId, runnerTimeline, simulator, blue).test(entry),
                entry -> {
                    try {
                        List<Node> events = new ArrayList<>();
                        if (initiateContractEntryBlueId.equals(entry.getThread())) {
                            events.add(blue.objectToNode(entry));
                        } else {
                            ContractUpdateAction action = (ContractUpdateAction) entry.getMessage();
                            if (action.getEmittedEvents() != null) {
                                events.addAll(action.getEmittedEvents());
                            }
                        }
                        for (Node event : events) {
                            eventQueue.put(event);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
        );

        List<ContractUpdateAction> actions = initiateContract(contract);
        contractUpdateActions.addAll(actions);
        actions.forEach(action -> simulator.appendEntry(runnerTimeline, initiateContractProcessingEntryBlueId, action));
    }

    private void processEvents() {
        while (isRunning || !eventQueue.isEmpty()) {
            try {
                Node event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    processContractEvent(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processContractEvent(Node event) {
        System.out.println("ContractRunner is processing new contract event");
        ContractUpdateAction lastUpdate = getLastContractUpdate();
        int epoch = lastUpdate != null ? lastUpdate.getEpoch() : 0;
        List<ContractUpdateAction> result = contractProcessor.processEvent(
                event.clone(),
                lastUpdate != null ? blue.clone(lastUpdate.getContractInstance()) : null,
                initiateContractEntryBlueId,
                initiateContractProcessingEntryBlueId,
                epoch + 1
        );
        System.out.println("Event processed. Number of new update actions: " + result.size());
        contractUpdateActions.addAll(result);
        result.forEach(action -> simulator.appendEntry(runnerTimeline, initiateContractProcessingEntryBlueId, action));
    }

    public void stop() throws InterruptedException {
        isRunning = false;
        processingThread.join(5000); // Wait up to 5 seconds for processing to complete
        System.out.println("ContractRunnerMT stopped");
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