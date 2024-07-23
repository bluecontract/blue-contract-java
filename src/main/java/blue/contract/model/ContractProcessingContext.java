package blue.contract.model;

import blue.contract.StepProcessorProvider;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.BlueIds;
import blue.language.utils.NodePathAccessor;
import blue.language.utils.NodeToObject;

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
    private int epoch;
    private List<Node> emittedEvents = new ArrayList<>();;
    private List<ContractInstance> contractInstances;
    private int startedLocalContracts;
    private String initiateContractEntryBlueId;
    private String initiateContractProcessingEntryBlueId;
    private Node previousContractInstance;
    private StepProcessorProvider stepProcessorProvider;
    private Blue blue;

    public Node getContract() {
        return contract;
    }

    public int getContractInstanceId() {
        return contractInstanceId;
    }

    public int getEpoch() {
        return epoch;
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

    public Node getPreviousContractInstance() {
        return previousContractInstance;
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

    public ContractProcessingContext epoch(int epoch) {
        this.epoch = epoch;
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

    public ContractProcessingContext initiateContractProcessingEntryBlueId(String initiateContractProcessingEntryBlueId) {
        this.initiateContractProcessingEntryBlueId = initiateContractProcessingEntryBlueId;
        return this;
    }

    public ContractProcessingContext previousContractInstance(Node previousContractInstance) {
        this.previousContractInstance = previousContractInstance;
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

    public Object accessContract(String path, boolean useGlobalScope, boolean resolveFinalLink) {
        Function<Node, Node> linkingProvider = useGlobalScope ? this::linkingProviderImplementation : null;
        Object result = NodePathAccessor.get(contract, path, linkingProvider, resolveFinalLink);
        if (result instanceof Node) {
            result = NodeToObject.get((Node) result, SIMPLE);
        } else if (result instanceof BigInteger) {
            BigInteger bigInt = (BigInteger) result;
            return isInJavaScriptSafeRange(bigInt) ? bigInt.longValue() : bigInt;
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

    private boolean isInJavaScriptSafeRange(BigInteger number) {
        BigInteger min = new BigInteger("-9007199254740991");
        BigInteger max = new BigInteger("9007199254740991");

        return (number.compareTo(min) >= 0) && (number.compareTo(max) <= 0);
    }

}