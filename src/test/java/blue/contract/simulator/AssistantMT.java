package blue.contract.simulator;

import blue.contract.model.*;
import blue.contract.model.step.ExpectEventStep;
import blue.contract.processor.ExpectEventStepProcessor;
import blue.contract.simulator.model.SimulatorTimelineEntry;
import blue.contract.utils.ExpressionEvaluator;
import blue.contract.utils.JSExecutor;
import blue.language.Blue;
import blue.language.model.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class AssistantMT {
    private final Blue blue;
    private final String initiateContractEntryBlueId;
    private String assistantTimeline;
    private String runnerTimeline;
    private SimulatorMT simulator;
    private Map<ProcessorKey, AssistantProcessor<?, ?>> processors = new HashMap<>();
    private volatile boolean isRunning = true;

    public AssistantMT(Blue blue, String initiateContractEntryBlueId) {
        this.blue = blue;
        this.initiateContractEntryBlueId = initiateContractEntryBlueId;
        System.out.println("AssistantMT created with initiateContractEntryBlueId: " + initiateContractEntryBlueId);
    }

    public void start(String assistantTimeline, String runnerTimeline, SimulatorMT simulator) {
        this.assistantTimeline = assistantTimeline;
        this.runnerTimeline = runnerTimeline;
        this.simulator = simulator;

        System.out.println("AssistantMT started. Subscribing to ContractUpdateActions on runnerTimeline: " + runnerTimeline);
        simulator.subscribe(
                entry -> {
                    System.out.println("Assistant is checking condition");
                    boolean result = runnerTimeline.equals(entry.getTimeline()) && entry.getMessage() instanceof ContractUpdateAction;
                    System.out.println("RunnerTimeline: " + runnerTimeline);
                    System.out.println("Entry timeline: " + entry.getTimeline());
                    System.out.println("Entry message: " + entry.getMessage().getClass());
                    System.out.println("Result is " + result);
                    return result;
                },
                this::processContractUpdateAction
        );
    }

    public <Req, Res> void registerProcessor(Class<Req> requestClass, Class<Res> responseClass, AssistantProcessor<Req, Res> processor) {
        ProcessorKey key = new ProcessorKey(requestClass, responseClass);
        processors.put(key, processor);
        System.out.println("Registered processor for request type " + requestClass.getSimpleName() +
                           " and response type " + responseClass.getSimpleName());
    }

    private void processContractUpdateAction(SimulatorTimelineEntry<Object> entry) {
        if (!isRunning) return;

        CompletableFuture.runAsync(() -> {
            System.out.println("Processing ContractUpdateAction");
            ContractUpdateAction action = (ContractUpdateAction) entry.getMessage();
            List<Node> pendingSteps = new ArrayList<>();

            processContractInstance(action.getContractInstance(), pendingSteps);
            if (action.getContractInstance().getProcessingState().getLocalContractInstances() != null) {
                for (ContractInstance localInstance : action.getContractInstance().getProcessingState().getLocalContractInstances()) {
                    processContractInstance(localInstance, pendingSteps);
                }
            }

            System.out.println("Found " + pendingSteps.size() + " pending steps to process");
            for (Node step : pendingSteps) {
                processStep(step);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <Req, Res> void processStep(Node step) {
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
            System.out.println("Appending processed result to timeline:");
            System.out.println(blue.objectToSimpleYaml(result));
            simulator.appendEntry(assistantTimeline, initiateContractEntryBlueId, result);
        } else {
            System.out.println("Error: No processor found for the given request and response types");
            throw new RuntimeException("No processor found for request type " + requestClass.getSimpleName() +
                                       (responseClass != null ? " and response type " + responseClass.getSimpleName() : ""));
        }
    }

    private void processContractInstance(ContractInstance instance, List<Node> pendingSteps) {
        System.out.println("Processing ContractInstance");
        ProcessingState processingState = instance.getProcessingState();
        if (processingState != null && processingState.getWorkflowInstances() != null) {
            for (WorkflowInstance workflowInstance : processingState.getWorkflowInstances()) {
                processWorkflowInstance(workflowInstance, instance, pendingSteps);
            }
        }
    }

    private void processWorkflowInstance(WorkflowInstance workflowInstance, ContractInstance contractInstance, List<Node> pendingSteps) {
        System.out.println("Processing WorkflowInstance: " + workflowInstance.getWorkflow().getName());
        if (!workflowInstance.isCompleted() && workflowInstance.getCurrentStepName() != null) {
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
                            Node evaluated = evaluateStep(stepNode, workflowInstance, contractInstance);
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

    private Node evaluateStep(Node stepNode, WorkflowInstance workflowInstance, ContractInstance contractInstance) {
        JSExecutor jsExecutor = new JSExecutor(blue);
        ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(jsExecutor);
        ExpectEventStepProcessor processor = new ExpectEventStepProcessor(stepNode, expressionEvaluator);

        ContractProcessingContext contractContext = new ContractProcessingContext()
                .contract(contractInstance.getContractState())
                .contractInstanceId(contractInstance.getId())
                .blue(blue);
        WorkflowProcessingContext workflowContext = new WorkflowProcessingContext()
                .workflowInstance(workflowInstance)
                .contractProcessingContext(contractContext);

        return processor.extractExpectedEvent(workflowContext, blue);
    }

    public void stop() {
        isRunning = false;
        System.out.println("AssistantMT stopped");
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