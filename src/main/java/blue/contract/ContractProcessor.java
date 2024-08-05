package blue.contract;

import blue.contract.exception.ContractProcessingException;
import blue.contract.model.ContractInstance;
import blue.contract.model.ProcessingState;
import blue.contract.model.ContractProcessingContext;
import blue.contract.model.ContractUpdate;
import blue.contract.model.WorkflowInstance;
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
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class ContractProcessor {

    private StepProcessorProvider stepProcessorProvider;
    private WorkflowProcessor workflowProcessor;
    private Blue blue;

    public ContractProcessor(StepProcessorProvider stepProcessorProvider, Blue blue) {
        this.blue = blue;
        this.stepProcessorProvider = stepProcessorProvider;
        this.workflowProcessor = new WorkflowProcessor(stepProcessorProvider);
    }

    public ContractUpdate initiate(Node contract,
                                   String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        return initiate(contract, null, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
    }

    public ContractUpdate initiate(Node contract, ContractProcessingContext initiatedContext,
                                   String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        Node event = initializationEvent();
        Node preprocessedContract = preprocess(contract);

        ContractInstance rootInstance = new ContractInstance()
                .id(ContractInstance.ROOT_INSTANCE_ID)
                .contractState(preprocessedContract)
                .processingState(new ProcessingState());

        List<ContractInstance> contractInstances = new ArrayList<>();
        contractInstances.add(rootInstance);

        ContractProcessingContext context = initiatedContext != null ? initiatedContext :
                new ContractProcessingContext()
                        .contractInstances(contractInstances)
                        .contractInstanceId(ContractInstance.ROOT_INSTANCE_ID)
                        .contract(preprocessedContract)
                        .startedLocalContracts(0)
                        .initiateContractEntryBlueId(initiateContractEntryBlueId)
                        .initiateContractProcessingEntryBlueId(initiateContractProcessingEntryBlueId)
                        .stepProcessorProvider(stepProcessorProvider)
                        .blue(blue);

        List<WorkflowInstance> workflowInstances = processWorkflows(event, context, 0);
        int startedWorkflowInstancesCount = workflowInstances.size();
        workflowInstances.removeIf(WorkflowInstance::isCompleted);

        rootInstance.getProcessingState()
                .startedWorkflowsCount(startedWorkflowInstancesCount)
                .workflowInstances(workflowInstances.isEmpty() ? null : workflowInstances)
                .startedLocalContractsCount(context.getStartedLocalContracts())
                .localContractInstances(context.getContractInstances().size() > 1 ?
                        context.getContractInstances().subList(1, context.getContractInstances().size()) : null)
                .completed(context.isCompleted())
                .terminatedWithError(context.isTerminatedWithError());

        rootInstance.contractState(context.getContract());

        return new ContractUpdate()
                .contractInstance(rootInstance)
                .emittedEvents(context.getEmittedEvents().isEmpty() ? null : context.getEmittedEvents())
                .initiateContractEntry(new Node().blueId(initiateContractEntryBlueId))
                .initiateContractProcessingEntry(new Node().blueId(initiateContractProcessingEntryBlueId));
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

    public ContractUpdate processEvent(Node event, ContractUpdate contractUpdate) {
        ContractUpdate result = processEvent(event, contractUpdate.getContractInstance(),
                contractUpdate.getInitiateContractEntry().getAsText("/blueId"),
                contractUpdate.getInitiateContractProcessingEntry().getAsText("/blueId"));
        result.epoch(contractUpdate.getEpoch() + 1);
        return result;
    }

    public ContractUpdate processEvent(Node event, ContractInstance contractInstance,
                                       String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {

        if (contractInstance.getProcessingState().isCompleted() || contractInstance.getProcessingState().isTerminatedWithError()) {
            throw new IllegalStateException("Contract instance is already completed or terminated with error.");
        }

        int initialStartedLocalContracts = contractInstance.getProcessingState().getStartedLocalContractCount();
        String previousContractInstanceBlueId = calculateBlueId(contractInstance);

        List<ContractInstance> contractInstances = new ArrayList<>();
        contractInstances.add(contractInstance);
        if (contractInstance.getProcessingState().getLocalContractInstances() != null) {
            contractInstances.addAll(contractInstance.getProcessingState().getLocalContractInstances());
        }

        ContractProcessingContext context = new ContractProcessingContext()
                .contractInstances(contractInstances)
                .contractInstanceId(contractInstance.getId())
                .contract(contractInstance.getContractState())
                .startedLocalContracts(initialStartedLocalContracts)
                .initiateContractEntryBlueId(initiateContractEntryBlueId)
                .initiateContractProcessingEntryBlueId(initiateContractProcessingEntryBlueId)
                .stepProcessorProvider(stepProcessorProvider)
                .blue(blue);

        for (ContractInstance instance : contractInstances) {
            processEventInContractInstance(event, instance, context);
            if (context.isCompleted()) {
                break;
            }
        }

        List<ContractInstance> localContractInstances = contractInstances.stream()
                .filter(instance -> instance.getId() != ContractInstance.ROOT_INSTANCE_ID)
                .collect(Collectors.toList());

        contractInstance.getProcessingState()
                .startedLocalContractsCount(context.getStartedLocalContracts())
                .localContractInstances(localContractInstances.isEmpty() ? null : localContractInstances);

        return new ContractUpdate()
                .contractInstance(contractInstance)
                .contractInstancePrev(new Node().blueId(previousContractInstanceBlueId))
                .emittedEvents(context.getEmittedEvents().isEmpty() ? null : context.getEmittedEvents())
                .initiateContractEntry(new Node().blueId(initiateContractEntryBlueId))
                .initiateContractProcessingEntry(new Node().blueId(initiateContractProcessingEntryBlueId));
    }

    private void processEventInContractInstance(Node event, ContractInstance contractInstance, ContractProcessingContext context) {
        context.contract(contractInstance.getContractState());
        context.contractInstanceId(contractInstance.getId());

        List<WorkflowInstance> existingWorkflowInstances = contractInstance.getProcessingState().getWorkflowInstances();
        int startedWorkflows = contractInstance.getProcessingState().getStartedWorkflowCount();

        List<WorkflowInstance> newWorkflowInstances = processWorkflows(event, context, startedWorkflows);
        startedWorkflows += newWorkflowInstances.size();
        if (existingWorkflowInstances != null) {
            existingWorkflowInstances.forEach(workflowInstance -> workflowProcessor.processEvent(event, workflowInstance, context));
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
    }

    private List<WorkflowInstance> processWorkflows(Node event, ContractProcessingContext context, int initialStartedWorkflows) {
        Node workflowsNode = context.getContract().getProperties().get("workflows");
        if (workflowsNode == null)
            return new ArrayList<>();

        List<Node> workflows = workflowsNode.getItems();
        AtomicInteger currentId = new AtomicInteger(initialStartedWorkflows);
        List<WorkflowInstance> processedWorkflows = new ArrayList<>();

        if (workflows != null) {
            for (Node workflow : workflows) {
                try {
                    Optional<WorkflowInstance> result = workflowProcessor.processEvent(event, workflow, context);
                    if (result.isPresent()) {
                        result.get().id(currentId.getAndIncrement());
                        processedWorkflows.add(result.get());
                        if (context.isCompleted())
                            return processedWorkflows;
                    }
                } catch (ContractProcessingException e) {
                    break;
                }
            }
        }

        return processedWorkflows;
    }

    private Node initializationEvent() {
        return new Node().type(new Node().blueId(CONTRACT_INITIALIZATION_EVENT_BLUE_ID));
    }

    private String calculateBlueId(ContractInstance contractInstance) {
        return BlueIdCalculator.calculateBlueId(toNode(contractInstance));
    }

    private Node toNode(ContractInstance contractInstance) {
        return YAML_MAPPER.convertValue(contractInstance, Node.class);
    }

}
