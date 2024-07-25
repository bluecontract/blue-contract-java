package blue.contract.model;

import blue.contract.StepProcessorProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

public class WorkflowProcessingContext {
    private WorkflowInstance workflowInstance;
    private ContractProcessingContext contractProcessingContext;
    private StepProcessorProvider stepProcessorProvider;
    private WorkflowProcessingTransaction transaction;

    public WorkflowProcessingContext(WorkflowInstance workflowInstance, ContractProcessingContext contractProcessingContext, StepProcessorProvider stepProcessorProvider) {
        this.workflowInstance = workflowInstance;
        this.contractProcessingContext = contractProcessingContext;
        this.stepProcessorProvider = stepProcessorProvider;
    }

    public WorkflowInstance getWorkflowInstance() {
        return workflowInstance;
    }

    public ContractProcessingContext getContractProcessingContext() {
        return contractProcessingContext;
    }

    public StepProcessorProvider getStepProcessorProvider() {
        return stepProcessorProvider;
    }

    public void beginTransaction() {
        this.transaction = new WorkflowProcessingTransaction()
                .contract(contractProcessingContext.getContract().clone())
                .emittedEvents(new ArrayList<>(contractProcessingContext.getEmittedEvents()))
                .contractInstances(contractProcessingContext.getContractInstances().stream()
                        .map(ContractInstance::clone)
                        .collect(Collectors.toList()))
                .startedLocalContracts(contractProcessingContext.getStartedLocalContracts())
                .contractCompleted(contractProcessingContext.isCompleted())
                .terminatedWithError(contractProcessingContext.isTerminatedWithError())
                .workflowCurrentStepName(workflowInstance.getCurrentStepName())
                .workflowStepResults(new HashMap<>(workflowInstance.getStepResults()))
                .workflowCompleted(workflowInstance.isCompleted())
                .workflowNestedWorkflowInstance(workflowInstance.getNestedWorkflowInstance() != null ?
                        workflowInstance.getNestedWorkflowInstance().clone() : null);
    }

    public void commitTransaction() {
        this.transaction = null;
    }

    public void rollbackTransaction() {
        if (this.transaction != null) {
            contractProcessingContext.contract(transaction.getContract());
            contractProcessingContext.emittedEvents(transaction.getEmittedEvents());
            contractProcessingContext.contractInstances(transaction.getContractInstances());
            contractProcessingContext.startedLocalContracts(transaction.getStartedLocalContracts());
            contractProcessingContext.completed(transaction.isContractCompleted());
            contractProcessingContext.terminatedWithError(transaction.isTerminatedWithError());

            workflowInstance.currentStepName(transaction.getWorkflowCurrentStepName());
            workflowInstance.stepResults(transaction.getWorkflowStepResults());
            workflowInstance.completed(transaction.isWorkflowCompleted());
            workflowInstance.nestedWorkflowInstance(transaction.getWorkflowNestedWorkflowInstance());

            this.transaction = null;
        }
    }
}