package blue.contract;

import blue.contract.model.ContractInstance;
import blue.contract.model.ContractProcessingContext;
import blue.contract.model.ContractUpdate;
import blue.contract.model.WorkflowInstance;
import blue.language.Blue;
import blue.language.NodeProvider;
import blue.language.model.Node;
import blue.language.utils.BlueIdCalculator;
import blue.language.utils.limits.Limits;
import blue.language.utils.limits.PathLimits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
        ContractProcessingContext context = initiatedContext != null ? initiatedContext :
                new ContractProcessingContext()
                        .contract(preprocessedContract)
                        .contractInstances(new ArrayList<>())
                        .startedLocalContracts(0)
                        .initiateContractEntryBlueId(initiateContractEntryBlueId)
                        .initiateContractProcessingEntryBlueId(initiateContractProcessingEntryBlueId)
                        .stepProcessorProvider(stepProcessorProvider)
                        .blue(blue);

        List<WorkflowInstance> workflowInstances = processWorkflows(event, context, 0);
        int startedWorkflowInstancesCount = workflowInstances.size();
        workflowInstances.removeIf(WorkflowInstance::isFinished);

        ContractInstance instance = new ContractInstance()
                .contract(context.getContract())
                .startedWorkflowsCount(startedWorkflowInstancesCount)
                .workflowInstances(workflowInstances.isEmpty() ? null : workflowInstances)
                .startedLocalContractsCount(context.getContractInstances().size())
                .localContractInstances(context.getContractInstances().isEmpty() ? null : context.getContractInstances());

        return new ContractUpdate()
                .contractInstance(instance)
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

    public ContractUpdate processEvent(Node event, ContractInstance contractInstance,
                                       String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        int initialStartedLocalContracts = contractInstance.getStartedLocalContractCount();
        String previousContractInstanceBlueId = calculateBlueId(contractInstance);

        List<ContractInstance> initialContractInstances = new ArrayList<>();
        if (contractInstance.getLocalContractInstances() != null)
            initialContractInstances.addAll(contractInstance.getLocalContractInstances());

        List<ContractInstance> contractInstances = new ArrayList<>();
        contractInstances.add(contractInstance);
        contractInstances.addAll(initialContractInstances);

        ContractProcessingContext context = new ContractProcessingContext()
                .contractInstances(contractInstances)
                .startedLocalContracts(initialStartedLocalContracts)
                .initiateContractEntryBlueId(initiateContractEntryBlueId)
                .initiateContractProcessingEntryBlueId(initiateContractProcessingEntryBlueId)
                .epoch(contractInstance.getEpoch() + 1)
                .previousContractInstance(new Node().blueId(previousContractInstanceBlueId))
                .stepProcessorProvider(stepProcessorProvider)
                .blue(blue);

        processEventInContractInstance(event, contractInstance, context);
        initialContractInstances.forEach(instance -> processEventInContractInstance(event, instance, context));

        List<ContractInstance> localContractInstances = contractInstances.stream()
                .filter(instance -> !instance.equals(contractInstance))
                .collect(Collectors.toList());
        contractInstance
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
        Node contract = contractInstance.getContract();
        String prevContractBlueId = BlueIdCalculator.calculateBlueId(contract);
        context.contract(contract);
        context.contractInstanceId(contractInstance.getId());
        List<WorkflowInstance> existingWorkflowInstances = contractInstance.getWorkflowInstances();
        int startedWorkflows = contractInstance.getStartedWorkflowCount();

        List<WorkflowInstance> newWorkflowInstances = processWorkflows(event, context, startedWorkflows);
        startedWorkflows += newWorkflowInstances.size();

        boolean existingWorkflowsChanged = false;
        if (existingWorkflowInstances != null) {
            for (WorkflowInstance workflowInstance : existingWorkflowInstances) {
                Optional<WorkflowInstance> processedWorkflow = workflowProcessor.processEvent(event, workflowInstance, context);
                if (processedWorkflow.isPresent()) {
                    existingWorkflowsChanged = true;
                }
            }
        }

        List<WorkflowInstance> updatedWorkflowInstances = new ArrayList<>();
        if (existingWorkflowInstances != null)
            updatedWorkflowInstances.addAll(existingWorkflowInstances);
        updatedWorkflowInstances.addAll(newWorkflowInstances);
        updatedWorkflowInstances.removeIf(WorkflowInstance::isFinished);

        boolean contractChanged = !context.getContract().getAsText("/blueId").equals(prevContractBlueId);
        boolean instanceChanged = contractChanged || !newWorkflowInstances.isEmpty() || existingWorkflowsChanged;

        contractInstance
                .contract(context.getContract())
                .epoch(context.getEpoch())
                .previousContractInstance(context.getPreviousContractInstance())
                .startedWorkflowsCount(startedWorkflows)
                .workflowInstances(updatedWorkflowInstances.isEmpty() ? null : updatedWorkflowInstances)
                .lastChangeContractInstance(contractChanged ? context.getPreviousContractInstance() : null)
                .lastContractChangeContractInstance(instanceChanged ? context.getPreviousContractInstance() : null);
    }

    private List<WorkflowInstance> processWorkflows(Node event, ContractProcessingContext context, int initialStartedWorkflows) {

        Node workflowsNode = context.getContract().getProperties().get("workflows");
        if (workflowsNode == null)
            return new ArrayList<>();

        List<Node> workflows = workflowsNode.getItems();
        AtomicInteger currentId = new AtomicInteger(initialStartedWorkflows);

        return workflows.stream()
                .map(workflow -> workflowProcessor.processEvent(event, workflow, context))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(instance -> instance.id(currentId.getAndIncrement()))
                .collect(Collectors.toList());
    }

    private Node initializationEvent() {
        return new Node().type(new Node().name("Contract Initialized"));
    }

    private String calculateBlueId(ContractInstance contractInstance) {
        return BlueIdCalculator.calculateBlueId(toNode(contractInstance));
    }

    private Node toNode(ContractInstance contractInstance) {
        return YAML_MAPPER.convertValue(contractInstance, Node.class);
    }

}
