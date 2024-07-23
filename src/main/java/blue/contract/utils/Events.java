package blue.contract.utils;

import blue.contract.model.ContractProcessingContext;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.event.ContractProcessingEvent;
import blue.language.model.Node;

public class Events {

    public static ContractProcessingEvent prepareContractProcessingEvent(Node event, String stepName,
                                                                         WorkflowProcessingContext workflowProcessingContext) {
        ContractProcessingContext contractProcessingContext = workflowProcessingContext.getContractProcessingContext();
        return new ContractProcessingEvent()
                .contractInstanceId(contractProcessingContext.getContractInstanceId())
                .workflowInstanceId(workflowProcessingContext.getWorkflowInstance().getId())
                .workflowStepName(stepName)
                .initiateContractEntry(contractProcessingContext.getInitiateContractEntryBlueId())
                .initiateContractProcessingEntry(contractProcessingContext.getInitiateContractProcessingEntryBlueId())
                .event(event);
    }

}
