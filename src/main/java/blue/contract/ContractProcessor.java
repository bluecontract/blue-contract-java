package blue.contract;

import blue.contract.model.ContractInstance;
import blue.contract.model.ContractUpdateAction;
import blue.contract.model.GenericContract;
import blue.contract.model.event.AgreedUponSimulatedEvent;
import blue.contract.model.event.ContractProcessingEvent;
import blue.contract.model.subscription.ContractSubscription;
import blue.contract.model.subscription.LocalContractSubscription;
import blue.language.Blue;
import blue.language.model.Node;

import java.util.*;

public class ContractProcessor {

    private final SingleEventContractProcessor singleEventProcessor;
    private final Blue blue;

    public ContractProcessor(StepProcessorProvider stepProcessorProvider, Blue blue) {
        this.singleEventProcessor = new SingleEventContractProcessor(stepProcessorProvider, blue);
        this.blue = blue;
    }

    public List<ContractUpdateAction> initiate(Node contract,
                                               String initiateContractEntryBlueId,
                                               String initiateContractProcessingEntryBlueId) {
        List<ContractUpdateAction> result = new ArrayList<>();

        ContractUpdateAction initialAction = singleEventProcessor.initiate(contract, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
        initialAction.epoch(0);
        result.add(initialAction);

        processAdditionalEvents(initialAction.getContractInstance(), result, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId, 1);

        return result;
    }

    public List<ContractUpdateAction> processEvent(Node event,
                                                   ContractInstance contractInstance,
                                                   String initiateContractEntryBlueId,
                                                   String initiateContractProcessingEntryBlueId,
                                                   int epoch) {
        List<ContractUpdateAction> actions = new ArrayList<>();

        ContractUpdateAction initialAction = singleEventProcessor.processEvent(event, contractInstance, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
        initialAction.epoch(epoch);
        actions.add(initialAction);

        processAdditionalEvents(initialAction.getContractInstance(), actions, initiateContractEntryBlueId, initiateContractProcessingEntryBlueId, epoch + 1);

        return actions;
    }

    private void processAdditionalEvents(ContractInstance contractInstance,
                                         List<ContractUpdateAction> actions,
                                         String initiateContractEntryBlueId,
                                         String initiateContractProcessingEntryBlueId,
                                         int startingEpoch) {
        ContractUpdateAction lastAction = actions.get(actions.size() - 1);
        if (lastAction.getEmittedEvents() == null || lastAction.getEmittedEvents().isEmpty()) {
            return;
        }

        List<ContractSubscription> mergedSubscriptions = mergeSubscriptions(contractInstance);
        Queue<Node> eventsToProcess = new LinkedList<>();

        processEmittedEvents(lastAction.getEmittedEvents(), eventsToProcess);

        int currentEpoch = startingEpoch;

        while (!eventsToProcess.isEmpty()) {
            Node event = eventsToProcess.poll();
            Optional<AgreedUponSimulatedEvent> agreedUponEvent = extractAgreedUponSimulatedEvent(event);
            boolean isAgreedUponEvent = agreedUponEvent.isPresent();

            if (isAgreedUponEvent || subscriptionApplies(event, mergedSubscriptions)) {
                if (isAgreedUponEvent) {
                    event = agreedUponEvent.get().getEvent();
                }
                ContractUpdateAction newAction = singleEventProcessor.processEvent(event, lastAction.getContractInstance(), initiateContractEntryBlueId, initiateContractProcessingEntryBlueId);
                newAction.epoch(currentEpoch++);
                actions.add(newAction);
                lastAction = newAction;

                if (newAction.getEmittedEvents() != null && !newAction.getEmittedEvents().isEmpty()) {
                    processEmittedEvents(newAction.getEmittedEvents(), eventsToProcess);
                }
            }
        }
    }

    private void processEmittedEvents(List<Node> emittedEvents, Queue<Node> eventsToProcess) {
        for (Node emittedEvent : emittedEvents) {
            Optional<AgreedUponSimulatedEvent> agreedUponSimulatedEvent = extractAgreedUponSimulatedEvent(emittedEvent);
            if (agreedUponSimulatedEvent.isPresent()) {
                eventsToProcess.add(agreedUponSimulatedEvent.get().getEvent());
                eventsToProcess.add(emittedEvent);
            } else {
                eventsToProcess.add(emittedEvent);
            }
        }
    }
    
    private Optional<AgreedUponSimulatedEvent> extractAgreedUponSimulatedEvent(Node event) {
        return blue.determineClass(event)
                .filter(ContractProcessingEvent.class::equals)
                .map(clazz -> blue.nodeToObject(event, ContractProcessingEvent.class))
                .map(ContractProcessingEvent::getEvent)
                .flatMap(innerEvent -> blue.determineClass(innerEvent)
                        .filter(AgreedUponSimulatedEvent.class::equals)
                        .map(clazz -> blue.nodeToObject(innerEvent, AgreedUponSimulatedEvent.class)));
    }

    private List<ContractSubscription> mergeSubscriptions(ContractInstance contractInstance) {
        List<ContractSubscription> mergedSubscriptions = new ArrayList<>();
        if (contractInstance.getContractState().getSubscriptions() != null) {
            mergedSubscriptions.addAll(contractInstance.getContractState().getSubscriptions());
        }

        if (contractInstance.getProcessingState().getLocalContractInstances() != null) {
            for (ContractInstance localInstance : contractInstance.getProcessingState().getLocalContractInstances()) {
                GenericContract localContract = localInstance.getContractState();
                if (localContract.getSubscriptions() != null) {
                    mergedSubscriptions.addAll(localContract.getSubscriptions());
                }
            }
        }

        return mergedSubscriptions;
    }

    private boolean subscriptionApplies(Node event, List<ContractSubscription> subscriptions) {
        return blue.determineClass(event)
                .filter(ContractProcessingEvent.class::equals)
                .map(clazz -> blue.nodeToObject(event, ContractProcessingEvent.class))
                .map(contractProcessingEvent -> subscriptions.stream()
                        .filter(LocalContractSubscription.class::isInstance)
                        .map(LocalContractSubscription.class::cast)
                        .anyMatch(subscription -> subscriptionMatchesEvent(subscription, contractProcessingEvent)))
                .orElse(false);
    }

    private boolean subscriptionMatchesEvent(LocalContractSubscription subscription, ContractProcessingEvent event) {
        return Optional.of(true)
                .filter(__ -> matchesContractInstance(subscription, event))
                .filter(__ -> matchesWorkflowInstance(subscription, event))
                .filter(__ -> matchesEventType(subscription, event))
                .isPresent();
    }

    private boolean matchesContractInstance(LocalContractSubscription subscription, ContractProcessingEvent event) {
        return subscription.getContractInstanceId() == null ||
               subscription.getContractInstanceId().equals(event.getContractInstanceId());
    }

    private boolean matchesWorkflowInstance(LocalContractSubscription subscription, ContractProcessingEvent event) {
        return subscription.getWorkflowInstanceId() == null ||
               subscription.getWorkflowInstanceId().equals(event.getWorkflowInstanceId());
    }

    private boolean matchesEventType(LocalContractSubscription subscription, ContractProcessingEvent event) {
        return subscription.getEvent() == null ||
               blue.nodeMatchesType(event.getEvent(), subscription.getEvent());
    }

    public SingleEventContractProcessor getSingleEventProcessor() {
        return singleEventProcessor;
    }
}