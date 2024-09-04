package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.model.*;
import blue.contract.model.event.ContractProcessingEvent;
import blue.contract.utils.ExpressionEvaluator;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.limits.PathLimits;

import java.util.Optional;

public class ExpectEventStepProcessor extends AbstractStepProcessor {

    public ExpectEventStepProcessor(Node step, ExpressionEvaluator expressionEvaluator) {
        super(step, expressionEvaluator);
    }

    @Override
    public Optional<WorkflowInstance> handleEvent(Node event, WorkflowProcessingContext context) {
        Blue blue = context.getContractProcessingContext().getBlue();

        Node expectedEventNode = extractExpectedEvent(context, blue);
        Object contract = extractContract(context, blue);

        Optional<Class<?>> eventClass = blue.determineClass(event);
        if (eventClass.isPresent()) {
            if (ContractProcessingEvent.class.equals(eventClass.get())) {
                ContractProcessingEvent contractProcessingEvent = blue.nodeToObject(event, ContractProcessingEvent.class);
                if (!matchesContract(contractProcessingEvent, contract, context)) {
                    return Optional.empty();
                }
                event = contractProcessingEvent.getEvent();
            } else if (contract != null) {
                return Optional.empty();
            }
        } else if (contract != null) {
            return Optional.empty();
        }

        ContractProcessingContext contractContext = context.getContractProcessingContext();
        boolean nodeMatchesType = contractContext.getBlue().nodeMatchesType(event, expectedEventNode);

        if (nodeMatchesType) {

            Optional<String> stepName = getStepName();
            if (stepName.isPresent()) {
                context.getWorkflowInstance().getStepResults().put(stepName.get(), event);
            }

            return finalizeNextStepByOrder(event, context);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<WorkflowInstance> finalizeEvent(Node event, WorkflowProcessingContext workflowProcessingContext) {
        WorkflowInstance workflowInstance = workflowProcessingContext.getWorkflowInstance();
        workflowInstance.currentStepName(step.getName());

        return Optional.of(workflowInstance);
    }


    private boolean matchesContractDetails(ContractProcessingEvent contractProcessingEvent,
                                           Integer contractInstanceId, String initiateContractEntryBlueId) {
        return (contractInstanceId == null || contractInstanceId.equals(contractProcessingEvent.getContractInstanceId())) &&
               initiateContractEntryBlueId.equals(contractProcessingEvent.getInitiateContractEntry().get("/blueId"));
    }

    public Node extractExpectedEvent(WorkflowProcessingContext context, Blue blue) {
        Node expectedEventNode = step.getProperties().get("event");
        if (expectedEventNode == null)
            throw new IllegalArgumentException("No \"event\" defined for step with name \"" +
                                               getStepName().orElse("<noname>") + "\" in workflow with name \"" +
                                               context.getWorkflowInstance().getWorkflow().getName() + "\".");
        blue.extend(expectedEventNode, PathLimits.withSinglePath("/"));
        expectedEventNode = evaluateExpressionsRecursively(expectedEventNode, context);
        return expectedEventNode;
    }

    private Object extractContract(WorkflowProcessingContext context, Blue blue) {
        Node contractNode = step.getProperties().get("contract");
        if (contractNode != null) {
            if (contractNode.getValue() != null) {
                Object contractObj = evaluateExpressionWithoutFinalLink(contractNode.getValue(), context);
                contractNode = blue.objectToNode(contractObj);
            } else {
                contractNode = evaluateExpressionsRecursively(contractNode, context);
            }
            blue.extend(contractNode, PathLimits.withSinglePath("/"));
            Optional<Class<?>> nodeClass = blue.determineClass(contractNode);
            if (!nodeClass.isPresent() ||
                (!nodeClass.get().equals(LocalContract.class) && !nodeClass.get().equals(ExternalContract.class))) {
                throw new IllegalArgumentException("Property \"contract\" must be either a \"Local Contract\" or \"External Contract\".");
            }

            return blue.nodeToObject(contractNode, nodeClass.get());
        }
        return null;
    }

    private boolean matchesContract(ContractProcessingEvent contractProcessingEvent, Object contract, WorkflowProcessingContext context) {
        ContractProcessingContext contractProcessingContext = context.getContractProcessingContext();
        int currentContractInstanceId = contractProcessingContext.getContractInstanceId();
        String currentInitiateContractEntryBlueId = contractProcessingContext.getInitiateContractEntryBlueId();
        if (contract == null) {
            return matchesContractDetails(contractProcessingEvent, currentContractInstanceId, currentInitiateContractEntryBlueId);
        } else if (contract instanceof LocalContract) {
            LocalContract localContract = (LocalContract) contract;
            return matchesContractDetails(contractProcessingEvent, localContract.getId(), currentInitiateContractEntryBlueId);
        } else if (contract instanceof ExternalContract) {
            ExternalContract externalContract = (ExternalContract) contract;
            return matchesContractDetails(contractProcessingEvent, (Integer) externalContract.getLocalContractInstanceId().getValue(),
                    (String) externalContract.getInitiateContractEntry().get("/blueId"));
        }
        return false;
    }
}