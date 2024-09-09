package blue.contract.model;

import blue.contract.StepProcessorProvider;
import blue.language.Blue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

public class WorkflowProcessingContext {
    private WorkflowInstance workflowInstance;
    private ContractProcessingContext contractProcessingContext;
    private StepProcessorProvider stepProcessorProvider;
    private WorkflowProcessingTransaction transaction;
    private Blue blue;

    public WorkflowProcessingContext() {}

    public WorkflowProcessingContext(WorkflowInstance workflowInstance, ContractProcessingContext contractProcessingContext, StepProcessorProvider stepProcessorProvider) {
        this.workflowInstance = workflowInstance;
        this.contractProcessingContext = contractProcessingContext;
        this.stepProcessorProvider = stepProcessorProvider;
        this.blue = contractProcessingContext.getBlue();
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
                .contract(blue.clone(contractProcessingContext.getContract()))
                .emittedEvents(new ArrayList<>(contractProcessingContext.getEmittedEvents()))
                .contractInstances(contractProcessingContext.getContractInstances().stream()
                        .map(instance -> blue.clone(instance))
                        .collect(Collectors.toList()))
                .startedLocalContracts(contractProcessingContext.getStartedLocalContracts())
                .contractCompleted(contractProcessingContext.isCompleted())
                .terminatedWithError(contractProcessingContext.isTerminatedWithError())
                .workflowCurrentStepName(workflowInstance.getCurrentStepName())
                .workflowStepResults(workflowInstance.getStepResults() == null ? null : new HashMap<>(workflowInstance.getStepResults()))
                .workflowCompleted(workflowInstance.isCompleted())
                .workflowNestedWorkflowInstance(workflowInstance.getNestedWorkflowInstance() != null ?
                        blue.clone(workflowInstance.getNestedWorkflowInstance()) : null);
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

    public WorkflowProcessingContext workflowInstance(WorkflowInstance workflowInstance) {
        this.workflowInstance = workflowInstance;
        return this;
    }

    public WorkflowProcessingContext contractProcessingContext(ContractProcessingContext contractProcessingContext) {
        this.contractProcessingContext = contractProcessingContext;
        return this;
    }

    public WorkflowProcessingContext stepProcessorProvider(StepProcessorProvider stepProcessorProvider) {
        this.stepProcessorProvider = stepProcessorProvider;
        return this;
    }
}