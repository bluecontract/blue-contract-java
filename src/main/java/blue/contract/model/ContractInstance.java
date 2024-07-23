package blue.contract.model;

import blue.language.model.Node;

import java.util.List;

public class ContractInstance {

    private int id;
    private Node contract;
    private int epoch;
    private Node previousContractInstance;
    private Node lastChangeContractInstance;
    private Node lastContractChangeContractInstance;
    private int startedWorkflowCount;
    private int startedLocalContractCount;
    private List<WorkflowInstance> workflowInstances;
    private List<ContractInstance> localContractInstances;

    public int getId() {
        return id;
    }

    public Node getContract() {
        return contract;
    }

    public int getEpoch() {
        return epoch;
    }

    public Node getPreviousContractInstance() {
        return previousContractInstance;
    }

    public Node getLastChangeContractInstance() {
        return lastChangeContractInstance;
    }

    public Node getLastContractChangeContractInstance() {
        return lastContractChangeContractInstance;
    }

    public int getStartedWorkflowCount() {
        return startedWorkflowCount;
    }

    public int getStartedLocalContractCount() {
        return startedLocalContractCount;
    }

    public List<WorkflowInstance> getWorkflowInstances() {
        return workflowInstances;
    }

    public List<ContractInstance> getLocalContractInstances() {
        return localContractInstances;
    }

    public ContractInstance id(int id) {
        this.id = id;
        return this;
    }

    public ContractInstance contract(Node contract) {
        this.contract = contract;
        return this;
    }

    public ContractInstance epoch(int epoch) {
        this.epoch = epoch;
        return this;
    }

    public ContractInstance previousContractInstance(Node previousContractInstance) {
        this.previousContractInstance = previousContractInstance;
        return this;
    }

    public ContractInstance lastChangeContractInstance(Node lastChangeContractInstance) {
        this.lastChangeContractInstance = lastChangeContractInstance;
        return this;
    }

    public ContractInstance lastContractChangeContractInstance(Node lastContractChangeContractInstance) {
        this.lastContractChangeContractInstance = lastContractChangeContractInstance;
        return this;
    }

    public ContractInstance startedWorkflowsCount(int startedWorkflowCount) {
        this.startedWorkflowCount = startedWorkflowCount;
        return this;
    }

    public ContractInstance startedLocalContractsCount(int startedLocalContractCount) {
        this.startedLocalContractCount = startedLocalContractCount;
        return this;
    }

    public ContractInstance workflowInstances(List<WorkflowInstance> workflowInstances) {
        this.workflowInstances = workflowInstances;
        return this;
    }

    public ContractInstance localContractInstances(List<ContractInstance> localContractInstances) {
        this.localContractInstances = localContractInstances;
        return this;
    }
}