package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.ContractProcessor;
import blue.contract.model.*;
import blue.contract.utils.ExpressionEvaluator;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.NodeToObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static blue.language.utils.NodeToObject.Strategy.SIMPLE;

public class InitializeLocalContractStepProcessor extends AbstractStepProcessor {

    public InitializeLocalContractStepProcessor(Node step, ExpressionEvaluator expressionEvaluator) {
        super(step, expressionEvaluator);
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
        Blue blue = workflowProcessingContext.getContractProcessingContext().getBlue();
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
            LocalContract result = new LocalContract()
                    .id(instance.getId());
            Map<String, Object> map = new HashMap<>();
            map.put("localContract", NodeToObject.get(blue.objectToNode(result), SIMPLE));
            workflowProcessingContext.getWorkflowInstance().getStepResults().put(stepName.get(), map);
        }

    }

}