package blue.contract.simulator;

import blue.contract.model.*;
import blue.contract.model.event.ContractProcessingEvent;
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
    private Simulator simulator;
    private Map<ProcessorKey, AssistantProcessor<?, ?>> processors = new HashMap<>();

    public Assistant(Blue blue, String initiateContractEntryBlueId) {
        this.blue = blue;
        this.initiateContractEntryBlueId = initiateContractEntryBlueId;
        System.out.println("Assistant created with initiateContractEntryBlueId: " + initiateContractEntryBlueId);
    }

    public void start(String assistantTimeline, String runnerTimeline, Simulator simulator) {
        this.assistantTimeline = assistantTimeline;
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

    private void processContractUpdateAction(SimulatorTimelineEntry<Object> entry) {
        System.out.println("Processing ContractUpdateAction");
        ContractUpdateAction action = (ContractUpdateAction) entry.getMessage();

        if (action.getEmittedEvents() != null) {
            for (Node eventNode : action.getEmittedEvents()) {
                Optional<Class<?>> eventClass = blue.determineClass(eventNode);
                if (eventClass.isPresent() && ContractProcessingEvent.class.equals(eventClass.get())) {
                    ContractProcessingEvent contractProcessingEvent = blue.nodeToObject(eventNode, ContractProcessingEvent.class);
                    processContractProcessingEvent(contractProcessingEvent);
                }
            }
        }
    }

    private void processContractProcessingEvent(ContractProcessingEvent contractProcessingEvent) {
        Node actualEventNode = contractProcessingEvent.getEvent();
        if (actualEventNode != null) {
            Optional<Class<?>> actualEventClass = blue.determineClass(actualEventNode);
            if (actualEventClass.isPresent() && AssistantTaskReadyEvent.class.equals(actualEventClass.get())) {
                AssistantTaskReadyEvent assistantTaskReadyEvent = blue.nodeToObject(actualEventNode, AssistantTaskReadyEvent.class);
                processAssistantTaskReadyEvent(assistantTaskReadyEvent);
            } else {
                System.out.println("Event is not an AssistantTaskReadyEvent: " + actualEventClass.orElse(null));
            }
        } else {
            System.out.println("ContractProcessingEvent contains no event");
        }
    }

    private void processAssistantTaskReadyEvent(AssistantTaskReadyEvent event) {
        System.out.println("Processing AssistantTaskReadyEvent");
        if (event.getTask() != null) {
            processStep(blue.objectToNode(event.getTask()));
        } else {
            System.out.println("Warning: AssistantTaskReadyEvent contains no AssistantTask");
        }
    }

    @SuppressWarnings("unchecked")
    private <Req, Res> void processStep(Node step) {
        System.out.println("Processing step: " + step.getDescription());
        AssistantTask<Req, Res> task = blue.nodeToObject(step, AssistantTask.class);

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