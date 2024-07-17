package blue.contract.model;

import blue.contract.StepProcessorProvider;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.BlueIds;
import blue.language.utils.NodeToObject;
import jdk.vm.ci.meta.Local;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static blue.language.utils.NodeToObject.Strategy.SIMPLE;

public class ContractProcessingContext {
    private Node contract;
    private int contractInstanceId;
    private List<Node> emittedEvents;
    private List<ContractInstance> contractInstances;
    private int startedLocalContracts;
    private String initiateContractEntryBlueId;
    private String initiateContractProcessingEntryBlueId;
    private StepProcessorProvider stepProcessorProvider;
    private Blue blue;

    public ContractProcessingContext(Node contract, List<ContractInstance> contractInstances, int startedLocalContracts,
                                     String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId,
                                     StepProcessorProvider stepProcessorProvider, Blue blue) {
        this.contract = contract;
        this.emittedEvents = new ArrayList<>();
        this.contractInstances = contractInstances;
        this.startedLocalContracts = startedLocalContracts;
        this.initiateContractEntryBlueId = initiateContractEntryBlueId;
        this.initiateContractProcessingEntryBlueId = initiateContractProcessingEntryBlueId;
        this.stepProcessorProvider = stepProcessorProvider;
        this.blue = blue;
    }

    public Node getContract() {
        return contract;
    }

    public int getContractInstanceId() {
        return contractInstanceId;
    }

    public List<Node> getEmittedEvents() {
        return emittedEvents;
    }

    public List<ContractInstance> getContractInstances() {
        return contractInstances;
    }

    public int getStartedLocalContracts() {
        return startedLocalContracts;
    }

    public String getInitiateContractEntryBlueId() {
        return initiateContractEntryBlueId;
    }

    public String getInitiateContractProcessingEntryBlueId() {
        return initiateContractProcessingEntryBlueId;
    }

    public StepProcessorProvider getStepProcessorProvider() {
        return stepProcessorProvider;
    }

    public Blue getBlue() {
        return blue;
    }

    public ContractProcessingContext contract(Node contract) {
        this.contract = contract;
        return this;
    }

    public ContractProcessingContext contractInstanceId(int contractInstanceId) {
        this.contractInstanceId = contractInstanceId;
        return this;
    }

    public ContractProcessingContext emittedEvents(List<Node> emittedEvents) {
        this.emittedEvents = emittedEvents;
        return this;
    }

    public ContractProcessingContext startedLocalContracts(int startedLocalContracts) {
        this.startedLocalContracts = startedLocalContracts;
        return this;
    }

    public ContractProcessingContext contractInstances(List<ContractInstance> contractInstances) {
        this.contractInstances = contractInstances;
        return this;
    }

    public ContractProcessingContext initiateContractEntryBlueId(String initiateContractEntryBlueId) {
        this.initiateContractEntryBlueId = initiateContractEntryBlueId;
        return this;
    }

    public ContractProcessingContext initiateContractProcessingEntry(String initiateContractProcessingEntryBlueId) {
        this.initiateContractProcessingEntryBlueId = initiateContractProcessingEntryBlueId;
        return this;
    }

    public ContractProcessingContext stepProcessorProvider(StepProcessorProvider stepProcessorProvider) {
        this.stepProcessorProvider = stepProcessorProvider;
        return this;
    }

    public ContractProcessingContext blue(Blue blue) {
        this.blue = blue;
        return this;
    }

    public Object accessContract(String path, boolean useGlobalScope) {
        Function<Node, Node> linkingProvider = useGlobalScope ? this::linkingProviderImplementation : null;
        Object result = contract.get(path, linkingProvider);
        if (result instanceof Node) {
            result = NodeToObject.get((Node) result, SIMPLE);
        }
        return result;
    }

    private Node linkingProviderImplementation(Node node) {
        if (node.getType() != null) {
            String localContractBlueId = BlueIds.getBlueId(LocalContract.class)
                    .orElseThrow(() -> new IllegalStateException("LocalContract class must have @BlueId annotation properly set."));
            if (localContractBlueId.equals(node.getType().get("/blueId"))) {
                BigInteger id = (BigInteger) node.getProperties().get("id").getValue();
                return contractInstances.stream()
                        .filter(instance -> instance.getId() == id.intValue())
                        .findFirst()
                        .map(ContractInstance::getContract)
                        .orElse(null);
            }
        }
        return null;
    }

}