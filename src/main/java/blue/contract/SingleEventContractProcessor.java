package blue.contract;

import blue.contract.debug.DebugContext;
import blue.contract.exception.ContractProcessingException;
import blue.contract.model.*;
import blue.contract.model.event.WorkflowInstanceStartedEvent;
import blue.contract.utils.Constants;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.BlueIdCalculator;
import blue.language.utils.limits.Limits;
import blue.language.utils.limits.PathLimits;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static blue.contract.Properties.CONTRACT_INITIALIZATION_EVENT_BLUE_ID;

public class SingleEventContractProcessor {
    private final StepProcessorProvider stepProcessorProvider;
    private final WorkflowProcessor workflowProcessor;
    private final Blue blue;
    private final ContractProcessorConfig contractProcessorConfig;
    private final DebugContext debugContext;

    public SingleEventContractProcessor(StepProcessorProvider stepProcessorProvider, Blue blue) {
        this(stepProcessorProvider, blue, new DebugContext(false));
    }

    public SingleEventContractProcessor(StepProcessorProvider stepProcessorProvider, Blue blue, DebugContext debugContext) {
        this.blue = blue;
        this.stepProcessorProvider = stepProcessorProvider;
        this.workflowProcessor = new WorkflowProcessor(stepProcessorProvider, debugContext);
        this.contractProcessorConfig = new ContractProcessorConfig();
        this.debugContext = debugContext;
    }

    public ContractUpdateAction initiate(Node contract, String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        return initiate(contract, null, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
    }

    public ContractUpdateAction initiate(GenericContract contract, ContractProcessingContext initiatedContext,
                                         String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        return initiate(blue.objectToNode(contract), initiatedContext, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
    }

    public ContractUpdateAction initiate(Node contract, ContractProcessingContext initiatedContext,
                                         String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        Node event = initializationEvent();

        Node preprocessedContractNode = preprocess(contract);
        GenericContract preprocessedContract = blue.nodeToObject(preprocessedContractNode, GenericContract.class);

        ContractInstance rootInstance = new ContractInstance()
                .id(Constants.ROOT_INSTANCE_ID)
                .contractState(preprocessedContract)
                .processingState(new ProcessingState());

        List<ContractInstance> contractInstances = new ArrayList<>();
        contractInstances.add(rootInstance);

        ContractProcessingContext context = initiatedContext != null ? initiatedContext :
                new ContractProcessingContext()
                        .contractInstances(contractInstances)
                        .contractInstanceId(Constants.ROOT_INSTANCE_ID)
                        .contract(preprocessedContract)
                        .startedLocalContracts(0)
                        .initiateContractEntryBlueId(initiateContractEntryBlueId)
                        .initiateContractProcessingEntryBlueId(initiateContractProcessingEntryBlueId)
                        .stepProcessorProvider(stepProcessorProvider)
                        .blue(blue)
                        .config(contractProcessorConfig)
                        .incomingEvent(event);

        debugContext.startContractProcessing(event, contract);

        List<WorkflowInstance> workflowInstances = processWorkflows(event, context, 0);
        int startedWorkflowInstancesCount = workflowInstances.size();
        workflowInstances.removeIf(WorkflowInstance::isCompleted);

        // Collect local contract instances excluding the root instance
        List<ContractInstance> localContractInstances = context.getContractInstances().stream()
                .filter(instance -> instance.getId() != Constants.ROOT_INSTANCE_ID)
                .collect(Collectors.toList());

        rootInstance.getProcessingState()
                .startedWorkflowsCount(startedWorkflowInstancesCount)
                .workflowInstances(workflowInstances.isEmpty() ? null : workflowInstances)
                .startedLocalContractsCount(context.getStartedLocalContracts())
                .localContractInstances(localContractInstances.isEmpty() ? null : new ArrayList<>(localContractInstances))
                .completed(context.isCompleted())
                .terminatedWithError(context.isTerminatedWithError());

        rootInstance.contractState(context.getContract());

        return new ContractUpdateAction()
                .contractInstance(rootInstance)
                .emittedEvents(context.getEmittedEvents().isEmpty() ? null : context.getEmittedEvents())
                .initiateContractEntry(initiateContractEntryBlueId)
                .initiateContractProcessingEntry(initiateContractProcessingEntryBlueId)
                .incomingEvent(event);
    }

    private Node preprocess(Node contract) {
        Limits contractLimits = new PathLimits.Builder()
                .addPath("/participants/*")
                .addPath("/properties/*")
                .addPath("/workflows/*/*")
                .addPath("/modules/*/*")
                .build();
        blue.extend(contract, contractLimits);
        return blue.resolve(contract);
    }

    public ContractUpdateAction processEvent(Node event, ContractInstance contractInstance,
                                             String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        if (contractInstance.getProcessingState().isCompleted() || contractInstance.getProcessingState().isTerminatedWithError()) {
            throw new IllegalStateException("Contract instance is already completed or terminated with error.");
        }
        contractInstance = blue.clone(contractInstance);
        int initialStartedLocalContracts = contractInstance.getProcessingState().getStartedLocalContractCount();
        String previousContractInstanceBlueId = calculateBlueId(contractInstance);

        List<ContractInstance> localContractInstances = contractInstance.getProcessingState().getLocalContractInstances();
        List<Integer> localContractIds = new ArrayList<>();
        if (localContractInstances != null) {
            localContractIds = localContractInstances.stream()
                    .map(ContractInstance::getId)
                    .sorted()
                    .collect(Collectors.toList());
        }

        ContractProcessingContext context = new ContractProcessingContext()
                .contractInstances(localContractInstances == null ? new ArrayList<>() : new ArrayList<>(localContractInstances))
                .contractInstanceId(contractInstance.getId())
                .contract(contractInstance.getContractState())
                .startedLocalContracts(initialStartedLocalContracts)
                .initiateContractEntryBlueId(initiateContractEntryBlueId)
                .initiateContractProcessingEntryBlueId(initiateContractProcessingEntryBlueId)
                .stepProcessorProvider(stepProcessorProvider)
                .blue(blue)
                .config(contractProcessorConfig)
                .incomingEvent(event);

        debugContext.startContractProcessing(event, blue.objectToNode(contractInstance.getContractState()));

        processEventInContractInstance(event, contractInstance, context);

        for (int id : localContractIds) {
            ContractInstance instance = context.getContractInstance(id);
            ContractInstance result = processEventInContractInstance(event, instance, context);
            if (context.isCompleted()) {
                break;
            }
            context.replaceContractInstance(id, result);
        }

        List<ContractInstance> processedLocalInstances = context.getContractInstances().stream()
                .filter(instance -> instance.getId() != Constants.ROOT_INSTANCE_ID)
                .collect(Collectors.toList());

        contractInstance.getProcessingState()
                .startedLocalContractsCount(context.getStartedLocalContracts())
                .localContractInstances(processedLocalInstances.isEmpty() ? null : new ArrayList<>(processedLocalInstances));

        return new ContractUpdateAction()
                .contractInstance(contractInstance)
                .contractInstancePrev(previousContractInstanceBlueId)
                .emittedEvents(context.getEmittedEvents().isEmpty() ? null : context.getEmittedEvents())
                .initiateContractEntry(initiateContractEntryBlueId)
                .initiateContractProcessingEntry(initiateContractProcessingEntryBlueId)
                .incomingEvent(event);
    }

    private ContractInstance processEventInContractInstance(Node event, ContractInstance contractInstance, ContractProcessingContext context) {
        context.contract(contractInstance.getContractState());
        context.contractInstanceId(contractInstance.getId());

        List<WorkflowInstance> existingWorkflowInstances = contractInstance.getProcessingState().getWorkflowInstances();
        int startedWorkflows = contractInstance.getProcessingState().getStartedWorkflowCount();

        List<WorkflowInstance> newWorkflowInstances = processWorkflows(event, context, startedWorkflows);
        addWorkflowInstanceStartedEvents(newWorkflowInstances, context);
        startedWorkflows += newWorkflowInstances.size();

        if (existingWorkflowInstances != null) {
            for (WorkflowInstance workflowInstance : existingWorkflowInstances) {
                debugContext.startWorkflowProcessing(workflowInstance.getWorkflow().getName(), workflowInstance.getStepResults());
                workflowProcessor.processEvent(event, workflowInstance, context);
            }
        }

        List<WorkflowInstance> updatedWorkflowInstances = new ArrayList<>();
        if (existingWorkflowInstances != null) {
            updatedWorkflowInstances.addAll(existingWorkflowInstances);
        }
        updatedWorkflowInstances.addAll(newWorkflowInstances);
        updatedWorkflowInstances.removeIf(WorkflowInstance::isCompleted);

        contractInstance.contractState(context.getContract());
        contractInstance.getProcessingState()
                .startedWorkflowsCount(startedWorkflows)
                .workflowInstances(updatedWorkflowInstances.isEmpty() ? null : updatedWorkflowInstances);

        return contractInstance;
    }

    private List<WorkflowInstance> processWorkflows(Node event, ContractProcessingContext context, int initialStartedWorkflows) {
        List<Workflow> workflows = context.getContract().getWorkflows();
        if (workflows == null) return new ArrayList<>();
        AtomicInteger currentId = new AtomicInteger(initialStartedWorkflows);
        List<WorkflowInstance> processedWorkflows = new ArrayList<>();
        for (Workflow workflow : workflows) {
            try {
                // TODO: convert
                debugContext.startWorkflowProcessing(workflow.getName());
                Optional<WorkflowInstance> result = workflowProcessor.processEvent(event, blue.objectToNode(workflow), context);
                if (result.isPresent()) {
                    result.get().id(currentId.getAndIncrement());
                    processedWorkflows.add(result.get());
                    if (context.isCompleted()) return processedWorkflows;
                }
            } catch (ContractProcessingException e) {
                break;
            }
        }
        return processedWorkflows;
    }

    private void addWorkflowInstanceStartedEvents(List<WorkflowInstance> newWorkflowInstances, ContractProcessingContext context) {
        for (WorkflowInstance workflowInstance : newWorkflowInstances) {
            WorkflowInstanceStartedEvent event = new WorkflowInstanceStartedEvent()
                    .workflowInstanceId(workflowInstance.getId())
                    .contractInstanceId(context.getContractInstanceId())
                    .currentStepName(workflowInstance.getCurrentStepName())
                    .initiateContractEntry(context.getInitiateContractEntryBlueId())
                    .initiateContractProcessingEntry(context.getInitiateContractProcessingEntryBlueId());
            Node eventNode = blue.objectToNode(event);
            context.getEmittedEvents().add(eventNode);
        }
    }

    private Node initializationEvent() {
        return new Node().type(new Node().blueId(CONTRACT_INITIALIZATION_EVENT_BLUE_ID));
    }

    private String calculateBlueId(ContractInstance contractInstance) {
        return BlueIdCalculator.calculateBlueId(toNode(contractInstance));
    }

    private Node toNode(ContractInstance contractInstance) {
        return blue.objectToNode(contractInstance);
    }

    public StepProcessorProvider getStepProcessorProvider() {
        return stepProcessorProvider;
    }

    public WorkflowProcessor getWorkflowProcessor() {
        return workflowProcessor;
    }

    public Blue getBlue() {
        return blue;
    }

    public ContractProcessorConfig getContractProcessorConfig() {
        return contractProcessorConfig;
    }
}