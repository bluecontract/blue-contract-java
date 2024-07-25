package blue.contract.model;

import blue.language.model.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorkflowProcessingTransaction {
    private Node contract;
    private List<Node> emittedEvents = new ArrayList<>();
    private List<ContractInstance> contractInstances;
    private int startedLocalContracts;
    private boolean contractCompleted;
    private boolean terminatedWithError;
    private String workflowCurrentStepName;
    private Map<String, Object> workflowStepResults;
    private boolean workflowCompleted;
    private WorkflowInstance workflowNestedWorkflowInstance;

    public Node getContract() {
        return contract;
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

    public boolean isContractCompleted() {
        return contractCompleted;
    }

    public boolean isTerminatedWithError() {
        return terminatedWithError;
    }

    public String getWorkflowCurrentStepName() {
        return workflowCurrentStepName;
    }

    public Map<String, Object> getWorkflowStepResults() {
        return workflowStepResults;
    }

    public boolean isWorkflowCompleted() {
        return workflowCompleted;
    }

    public WorkflowInstance getWorkflowNestedWorkflowInstance() {
        return workflowNestedWorkflowInstance;
    }

    public WorkflowProcessingTransaction contract(Node contract) {
        this.contract = contract;
        return this;
    }

    public WorkflowProcessingTransaction emittedEvents(List<Node> emittedEvents) {
        this.emittedEvents = emittedEvents;
        return this;
    }

    public WorkflowProcessingTransaction contractInstances(List<ContractInstance> contractInstances) {
        this.contractInstances = contractInstances;
        return this;
    }

    public WorkflowProcessingTransaction startedLocalContracts(int startedLocalContracts) {
        this.startedLocalContracts = startedLocalContracts;
        return this;
    }

    public WorkflowProcessingTransaction contractCompleted(boolean contractCompleted) {
        this.contractCompleted = contractCompleted;
        return this;
    }

    public WorkflowProcessingTransaction terminatedWithError(boolean terminatedWithError) {
        this.terminatedWithError = terminatedWithError;
        return this;
    }

    public WorkflowProcessingTransaction workflowCurrentStepName(String workflowCurrentStepName) {
        this.workflowCurrentStepName = workflowCurrentStepName;
        return this;
    }

    public WorkflowProcessingTransaction workflowStepResults(Map<String, Object> workflowStepResults) {
        this.workflowStepResults = workflowStepResults;
        return this;
    }

    public WorkflowProcessingTransaction workflowCompleted(boolean workflowCompleted) {
        this.workflowCompleted = workflowCompleted;
        return this;
    }

    public WorkflowProcessingTransaction workflowNestedWorkflowInstance(WorkflowInstance workflowNestedWorkflowInstance) {
        this.workflowNestedWorkflowInstance = workflowNestedWorkflowInstance;
        return this;
    }
}