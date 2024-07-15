package blue.contract.model;

import blue.language.model.Node;

import java.util.List;

public class ContractInstance {

    private int id;
    private Node contract;
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

    public ContractInstance startedWorkflowsCount(int startedWorkflowsCount) {
        this.startedWorkflowCount = startedWorkflowsCount;
        return this;
    }

    public ContractInstance startedLocalContractsCount(int startedLocalContractsCount) {
        this.startedLocalContractCount = startedLocalContractsCount;
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
