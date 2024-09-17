package blue.contract.debug;

import blue.contract.model.Contract;
import blue.language.model.Node;

import java.util.ArrayList;
import java.util.List;

public class DebugContractProcessingInfo {
    private Node contract;
    private Node incomingEvent;
    private List<DebugWorkflowProcessingInfo> workflowResults = new ArrayList<>();

    public Node getContract() {
        return contract;
    }

    public DebugContractProcessingInfo contract(Node contract) {
        this.contract = contract;
        return this;
    }

    public List<DebugWorkflowProcessingInfo> getWorkflowResults() {
        return workflowResults;
    }

    public DebugContractProcessingInfo workflowResults(List<DebugWorkflowProcessingInfo> workflowResults) {
        this.workflowResults = workflowResults;
        return this;
    }

    public Node getIncomingEvent() {
        return incomingEvent;
    }

    public DebugContractProcessingInfo incomingEvent(Node incomingEvent) {
        this.incomingEvent = incomingEvent;
        return this;
    }
}
