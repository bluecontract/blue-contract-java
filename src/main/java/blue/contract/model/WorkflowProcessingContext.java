package blue.contract.model;

import blue.contract.StepProcessorProvider;

public class WorkflowProcessingContext {
    private WorkflowInstance workflowInstance;
    private ContractProcessingContext contractProcessingContext;
    private StepProcessorProvider stepProcessorProvider;

    public WorkflowProcessingContext(WorkflowInstance workflowInstance, ContractProcessingContext contractProcessingContext,
                                     StepProcessorProvider stepProcessorProvider) {
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
