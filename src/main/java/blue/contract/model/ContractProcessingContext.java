package blue.contract.model;

import blue.contract.StepProcessorProvider;
import blue.contract.utils.JSExecutor;
import blue.language.Blue;
import blue.language.model.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public Object executeExpression(String value, WorkflowProcessingContext context) {
        String expr = value.replaceAll("^\\$\\{(.*)\\}$", "$1");
        try {
            Map<String, Object> bindings = new HashMap<>();
            // Add the contract function to the bindings
            bindings.put("contract", (java.util.function.Function<String, Object>) this::contractFunction);
            bindings.put("steps", context.getWorkflowInstance().getStepResults());

            return new JSExecutor(this).executeScript(expr, bindings);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error executing JS expression", e);
        }
    }

    private Object contractFunction(String path) {
        return new JSExecutor(this).contract(path);
    }
}