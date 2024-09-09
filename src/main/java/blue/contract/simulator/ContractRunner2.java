package blue.contract.simulator;

import blue.contract.ContractProcessor;
import blue.contract.SingleEventContractProcessor;
import blue.contract.model.Contract;
import blue.contract.model.ContractInstance;
import blue.contract.model.ContractUpdateAction;
import blue.contract.processor.StandardProcessorsProvider;
import blue.contract.simulator.model.SimulatorTimelineEntry;
import blue.contract.simulator.utils.ContractRunnerSubscriptionUtils;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.NodeToMapListOrValue;
import blue.language.utils.NodeTypeMatcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static blue.contract.model.ContractProcessorConfig.EVENT_TARGET_TYPE_TRANSFORMER;
import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class ContractRunner2 {

    private final Blue blue;
    private final ContractProcessor contractProcessor;
    private final String initiateContractEntryBlueId;
    private final String initiateContractProcessingEntryBlueId;
    private List<ContractUpdateAction> contractUpdateActions = new ArrayList<>();
    private Simulator simulator;

    public ContractRunner2(Blue blue, String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        System.out.println("Initializing ContractRunner2");
        StandardProcessorsProvider provider = new StandardProcessorsProvider(blue);
        this.blue = blue;
        this.contractProcessor = new ContractProcessor(provider, blue);
        this.initiateContractEntryBlueId = initiateContractEntryBlueId;
        this.initiateContractProcessingEntryBlueId = initiateContractProcessingEntryBlueId;
        System.out.println("ContractRunner2 initialized with initiateContractEntryBlueId: " + initiateContractEntryBlueId +
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
        return contractUpdateActions;
    }

    public ContractUpdateAction getLastContractUpdate() {
        if (contractUpdateActions.isEmpty()) {
            System.out.println("No contract updates available");
            return null;
        }
        System.out.println("Retrieving last contract update");
        return contractUpdateActions.get(contractUpdateActions.size() - 1);
    }

    public void startProcessingContract(Contract contract, String runnerTimeline, Simulator simulator) {

        System.out.println("Setting up subscription for contract events");
        simulator.subscribe(
                ContractRunnerSubscriptionUtils.createContractFilter(contract, initiateContractEntryBlueId, runnerTimeline, simulator),
                entry -> {
                    System.out.println("Processing new contract event");
                    int epoch = getLastContractUpdate().getEpoch();
                    System.out.println("Current epoch: " + epoch);

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
                        List<ContractUpdateAction> result = contractProcessor.processEvent(event,
                                ((ContractUpdateAction) ((SimulatorTimelineEntry) simulator.getTimelines().get(runnerTimeline).get(simulator.getTimelines().get(runnerTimeline).size() - 1)).getMessage()).getContractInstance(),
                                initiateContractEntryBlueId, initiateContractProcessingEntryBlueId, epoch + 1);
                        System.out.println("Event processed. Number of new update actions: " + result.size());
                        addContractUpdateActions(result);
                        System.out.println("Appending new contract update actions to simulator");
                        simulator.appendEntries(runnerTimeline, initiateContractProcessingEntryBlueId, result);
                    }
                });

        System.out.println("Starting to process contract on runnerTimeline: " + runnerTimeline);
        List<ContractUpdateAction> actions = initiateContract(contract);
        addContractUpdateActions(actions);
        System.out.println("Appending initial contract update actions to simulator");
        simulator.appendEntries(runnerTimeline, initiateContractProcessingEntryBlueId, actions);
    }

    private void addContractUpdateActions(List<ContractUpdateAction> actions) {
        actions.forEach(action -> contractUpdateActions.add(blue.clone(action)));
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