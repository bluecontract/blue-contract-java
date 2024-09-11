package blue.contract.simulator;

import blue.contract.model.*;
import blue.contract.model.step.ExpectEventStep;
import blue.contract.processor.ExpectEventStepProcessor;
import blue.contract.model.blink.SimulatorTimelineEntry;
import blue.contract.utils.ExpressionEvaluator;
import blue.contract.utils.JSExecutor;
import blue.language.Blue;
import blue.language.model.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Assistant {
    private final Blue blue;
    private final String initiateContractEntryBlueId;
    private String assistantTimeline;
    private String runnerTimeline;
    private Simulator simulator;
    private Map<ProcessorKey, AssistantProcessor<?, ?>> processors = new HashMap<>();

    public Assistant(Blue blue, String initiateContractEntryBlueId) {
        this.blue = blue;
        this.initiateContractEntryBlueId = initiateContractEntryBlueId;
        System.out.println("Assistant created with initiateContractEntryBlueId: " + initiateContractEntryBlueId);
    }

    public Assistant(Blue blue, String initiateContractEntryBlueId, String assistantTimeline) {
        this(blue, initiateContractEntryBlueId);
        this.assistantTimeline = assistantTimeline;
    }

    private boolean workedOnce = false;
    public void start(String assistantTimeline, String runnerTimeline, Simulator simulator) {
        this.assistantTimeline = assistantTimeline;
        this.runnerTimeline = runnerTimeline;
        this.simulator = simulator;

        System.out.println("Assistant started. Subscribing to ContractUpdateActions on runnerTimeline: " + runnerTimeline);
        simulator.subscribe(
                entry -> runnerTimeline.equals(entry.getTimeline()) && entry.getMessage() instanceof ContractUpdateAction,
                this::processContractUpdateAction
        );
    }

    public <Req, Res> void registerProcessor(Class<Req> requestClass, Class<Res> responseClass, AssistantProcessor<Req, Res> processor) {
        ProcessorKey key = new ProcessorKey(requestClass, responseClass);
        processors.put(key, processor);
        System.out.println("Registered processor for request type " + requestClass.getSimpleName() +
                           " and response type " + responseClass.getSimpleName());
    }

    public <Req, Res> List<AssistantTask<Req, Res>> getContractUpdateActionResults(TimelineEntry<Object> entry) {
        ContractUpdateAction action = (ContractUpdateAction) entry.getMessage();
        List<Node> pendingSteps = getPendingSteps(action);
        System.out.println("Found " + pendingSteps.size() + " pending steps to process");

        List<AssistantTask<Req, Res>> results = new ArrayList<>();
        for (Node step : pendingSteps) {
            results.add( processStep(step));
        }
        return results;
    }

    private <Req, Res> void processContractUpdateAction(SimulatorTimelineEntry<Object> entry) {
        workedOnce = true;
        System.out.println("Processing ContractUpdateAction");
        List<AssistantTask<Req, Res>> results = getContractUpdateActionResults(entry);

        for (AssistantTask<Req, Res> result : results) {
            System.out.println("Appending processed result to timeline:");
            System.out.println(blue.objectToSimpleYaml(result));
            simulator.appendEntry(assistantTimeline, initiateContractEntryBlueId, result);
        }
    }

    private List<Node> getPendingSteps(ContractUpdateAction action) {
        List<Node> pendingSteps = new ArrayList<>();
        processContractInstance(action.getContractInstance(), pendingSteps, action.getIncomingEvent());
        if (action.getContractInstance().getProcessingState().getLocalContractInstances() != null) {
            for (ContractInstance localInstance : action.getContractInstance().getProcessingState().getLocalContractInstances()) {
                processContractInstance(localInstance, pendingSteps, action.getIncomingEvent());
            }
        }
        return pendingSteps;
    }

    @SuppressWarnings("unchecked")
    private <Req, Res> AssistantTask<Req, Res> processStep(Node step) {
        System.out.println("Processing step: " + step.getDescription());
        SimulatorTimelineEntry<AssistantTask<Req, Res>> targetEntry =
                blue.nodeToObject(step, SimulatorTimelineEntry.class);
        AssistantTask<Req, Res> task = targetEntry.getMessage();

        Class<?> requestClass = task.getRequest().getClass();
        Class<?> responseClass = task.getResponse().getClass();

        System.out.println("Looking for processor for request type " + requestClass.getSimpleName() +
                           " and response type " + responseClass.getSimpleName());
        ProcessorKey key = new ProcessorKey(requestClass, responseClass);
        AssistantProcessor<Req, Res> processor = (AssistantProcessor<Req, Res>) processors.get(key);

        if (processor != null) {
            System.out.println("Processor found. Processing request...");
            Res response = processor.process(task.getRequest(), blue);
            AssistantTask<Req, Res> result = blue.clone(task);
            result.response(response);
            return result;
        } else {
            System.out.println("Error: No processor found for the given request and response types");
            throw new RuntimeException("No processor found for request type " + requestClass.getSimpleName() +
                                       (responseClass != null ? " and response type " + responseClass.getSimpleName() : ""));
        }
    }

    private void processContractInstance(ContractInstance instance, List<Node> pendingSteps, Node incomingEvent) {
        System.out.println("Processing ContractInstance");
        ProcessingState processingState = instance.getProcessingState();
        if (processingState != null && processingState.getWorkflowInstances() != null) {
            for (WorkflowInstance workflowInstance : processingState.getWorkflowInstances()) {
                processWorkflowInstance(workflowInstance, instance, pendingSteps, incomingEvent);
            }
        }
    }

    private void processWorkflowInstance(WorkflowInstance workflowInstance, ContractInstance contractInstance, List<Node> pendingSteps, Node incomingEvent) {
        System.out.println("Processing WorkflowInstance: " + workflowInstance.getWorkflow().getName());
        if (!workflowInstance.isCompleted() && workflowInstance.getCurrentStepName() != null) {
            System.out.println("Current Step Name: " + workflowInstance.getCurrentStepName());
            Node workflow = workflowInstance.getWorkflow();
            if (workflow != null && workflow.getProperties() != null) {
                Node stepsNode = workflow.getProperties().get("steps");
                if (stepsNode != null && stepsNode.getItems() != null) {
                    for (Node stepNode : stepsNode.getItems()) {
                        if (stepNode.getName() != null && !stepNode.getName().equals(workflowInstance.getCurrentStepName())) {
                            continue;
                        }
                        Optional<Class<?>> stepClass = blue.determineClass(stepNode);
                        if (stepClass.isPresent() && stepClass.get() == ExpectEventStep.class) {
                            Node evaluated = evaluateStep(stepNode, workflowInstance, contractInstance, incomingEvent);
                            if (evaluated != null && evaluated.getProperties().get("timeline").getBlueId().equals(assistantTimeline)) {
                                System.out.println("Found pending step: " + evaluated.getName());
                                pendingSteps.add(evaluated);
                            }
                        }
                    }
                }
            }
        }
    }

    private Node evaluateStep(Node stepNode, WorkflowInstance workflowInstance, ContractInstance contractInstance, Node incomingEvent) {
        JSExecutor jsExecutor = new JSExecutor(blue);
        ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(jsExecutor);
        ExpectEventStepProcessor processor = new ExpectEventStepProcessor(stepNode, expressionEvaluator);

        ContractProcessingContext contractContext = new ContractProcessingContext()
                .contract(contractInstance.getContractState())
                .contractInstanceId(contractInstance.getId())
                .incomingEvent(incomingEvent)
                .blue(blue);
        WorkflowProcessingContext workflowContext = new WorkflowProcessingContext()
                .workflowInstance(workflowInstance)
                .contractProcessingContext(contractContext);

        return processor.extractExpectedEvent(workflowContext, blue);
    }

    private static class ProcessorKey {
        private final Class<?> requestClass;
        private final Class<?> responseClass;

        public ProcessorKey(Class<?> requestClass, Class<?> responseClass) {
            this.requestClass = requestClass;
            this.responseClass = responseClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProcessorKey that = (ProcessorKey) o;
            return requestClass.equals(that.requestClass) && responseClass.equals(that.responseClass);
        }

        @Override
        public int hashCode() {
            return 31 * requestClass.hashCode() + responseClass.hashCode();
        }
    }
}