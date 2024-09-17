package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.SingleEventContractProcessor;
import blue.contract.debug.DebugContext;
import blue.contract.model.*;
import blue.contract.utils.ExpressionEvaluator;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.NodeToMapListOrValue;
import blue.language.utils.limits.Limits;
import blue.language.utils.limits.PathLimits;

import java.util.*;

import static blue.language.utils.NodeToMapListOrValue.Strategy.SIMPLE;
import static java.lang.Boolean.FALSE;

public class InitializeLocalContractStepProcessor extends AbstractStepProcessor {

    public InitializeLocalContractStepProcessor(Node step, ExpressionEvaluator expressionEvaluator) {
        super(step, expressionEvaluator);
    }

    @Override
    public Optional<WorkflowInstance> executeHandleStep(Node event, WorkflowProcessingContext context) {
        processEvent(event, context);
        return handleNextStepByOrder(event, context);
    }

    @Override
    public Optional<WorkflowInstance> executeFinalizeStep(Node event, WorkflowProcessingContext context) {
        processEvent(event, context);
        return finalizeNextStepByOrder(event, context);
    }

    private void processEvent(Node event, WorkflowProcessingContext workflowProcessingContext) {
        Blue blue = workflowProcessingContext.getContractProcessingContext().getBlue();
        Node extracted = extractContract(workflowProcessingContext, blue);
        GenericContract contractToInitialize = blue.nodeToObject(extracted, GenericContract.class);

        ContractProcessingContext contractProcessingContext = workflowProcessingContext.getContractProcessingContext();
        assignParticipants(contractToInitialize, contractProcessingContext.getContract());
        int instanceId = contractProcessingContext.getStartedLocalContracts() + 1;
        int currentContractInstanceId = contractProcessingContext.getContractInstanceId();
        GenericContract currentContract = contractProcessingContext.getContract();
        contractProcessingContext.contractInstanceId(instanceId);
        contractProcessingContext.contract(contractToInitialize);

        DebugContext newDebugContext = new DebugContext(getDebugContext().isDebug());
        SingleEventContractProcessor singleEventContractProcessor =
                new SingleEventContractProcessor(contractProcessingContext.getStepProcessorProvider(), blue, newDebugContext);
        ContractUpdateAction update = singleEventContractProcessor
                .initiate(extracted,
                        contractProcessingContext,
                        contractProcessingContext.getInitiateContractEntryBlueId(),
                        contractProcessingContext.getInitiateContractProcessingEntryBlueId());
        ContractInstance instance = update.getContractInstance();
        instance.id(instanceId);

        contractProcessingContext.getContractInstances().add(instance);
        contractProcessingContext.contractInstanceId(currentContractInstanceId);
        contractProcessingContext.contract(currentContract);
        contractProcessingContext.startedLocalContracts(contractProcessingContext.getStartedLocalContracts() + 1);

        Optional<String> stepName = getStepName();
        getDebugContext().addWorkflowStepResult(getStepName(), Map.of(
                "contractInitializationDebug", blue.objectToSimpleYaml(newDebugContext.getDebugInfo())
        ));
        if (stepName.isPresent()) {
            LocalContract result = new LocalContract()
                    .id(instance.getId());
            Map<String, Object> map = new HashMap<>();
            map.put("localContract", NodeToMapListOrValue.get(blue.objectToNode(result), SIMPLE));
            workflowProcessingContext.getWorkflowInstance().getStepResults().put(stepName.get(), map);
        }

    }

    private void assignParticipants(GenericContract contractToInitialize, GenericContract mainContract) {

        if (contractToInitialize.getMessaging() == null || contractToInitialize.getMessaging().getParticipants() == null) {
            return;
        }

        Map<String, Participant> participantsToInitialize = contractToInitialize.getMessaging().getParticipants();
        Map<String, Participant> currentParticipants = mainContract.getMessaging().getParticipants();

        List<String> participantKeys = new ArrayList<>(participantsToInitialize.keySet());
        for (String key : participantKeys) {
            if (!currentParticipants.containsKey(key)) {
                throw new RuntimeException("Participant with key '" + key + "' does not exist in the main contract.");
            }
            Participant participantToCopy = currentParticipants.get(key);
            participantsToInitialize.put(key, participantToCopy);
        }
    }

    public Node extractContract(WorkflowProcessingContext context, Blue blue) {
        Node contractNode = step.getProperties().get("contract");
        if (contractNode == null)
            throw new IllegalArgumentException("No \"contract\" defined for step with name \"" +
                                               getStepName().orElse("<noname>") + "\" in workflow with name \"" +
                                               context.getWorkflowInstance().getWorkflow().getName() + "\".");
        blue.extend(contractNode, PathLimits.withSinglePath("/"));
        contractNode = evaluateExpressionsRecursively(contractNode, context);
        return contractNode;
    }

    private Node preprocess(Node contract, Blue blue) {
        Limits contractLimits = new PathLimits.Builder()
                .addPath("/participants/*")
                .addPath("/properties/*")
                .addPath("/workflows/*/*")
                .addPath("/modules/*/*")
                .build();
        blue.extend(contract, contractLimits);

        return blue.resolve(contract);
    }

}