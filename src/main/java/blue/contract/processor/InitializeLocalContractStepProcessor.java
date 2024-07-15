package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.ContractProcessor;
import blue.contract.model.ContractInstance;
import blue.contract.model.ContractProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.contract.model.WorkflowProcessingContext;
import blue.language.model.Node;
import blue.language.utils.NodeToObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static blue.language.utils.NodeToObject.Strategy.SIMPLE;

public class InitializeLocalContractStepProcessor extends AbstractStepProcessor {

    public InitializeLocalContractStepProcessor(Node step) {
        super(step);
    }

    @Override
    public Optional<WorkflowInstance> handleEvent(Node event, WorkflowProcessingContext context) {
        processEvent(event, context);
        return handleNextStepByOrder(event, context);
    }

    @Override
    public Optional<WorkflowInstance> finalizeEvent(Node event, WorkflowProcessingContext context) {
        processEvent(event, context);
        return finalizeNextStepByOrder(event, context);
    }

    private void processEvent(Node event, WorkflowProcessingContext workflowProcessingContext) {
        Node contractToInitialize = step.getProperties().get("contract");

        ContractProcessingContext contractProcessingContext = workflowProcessingContext.getContractProcessingContext();
        ContractProcessor contractProcessor = new ContractProcessor(contractProcessingContext.getStepProcessorProvider());
        ContractInstance instance = contractProcessor
                .initiate(contractToInitialize, contractProcessingContext.getInitiateContractEntryBlueId(),
                        contractProcessingContext.getInitiateContractProcessingEntryBlueId())
                .getContractInstance();

        int nextNumber = contractProcessingContext.getStartedLocalContracts() + 1;
        contractProcessingContext.startedLocalContracts(nextNumber);
        instance.id(nextNumber);

        contractProcessingContext.getContractInstances().add(instance);
        Optional<String> stepName = getStepName();
        if (stepName.isPresent()) {
            Node result = new Node().type(new Node().name("Local Contract")).properties(
                    "id", new Node().value(instance.getId())
            );
            Map<String, Object> map = new HashMap<>();
            map.put("localContract", NodeToObject.get(result, SIMPLE));
            workflowProcessingContext.getWorkflowInstance().getStepResults().put(stepName.get(), map);
        }

    }

}