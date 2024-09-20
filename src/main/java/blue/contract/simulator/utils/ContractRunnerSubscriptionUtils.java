package blue.contract.simulator.utils;

import blue.contract.model.*;
import blue.contract.model.subscription.AllEventsExternalContractSubscription;
import blue.contract.simulator.LastEntryMessageRetriever;
import blue.contract.simulator.Simulator;
import blue.contract.simulator.SimulatorMT;
import blue.contract.model.blink.SimulatorTimelineEntry;
import blue.language.Blue;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class ContractRunnerSubscriptionUtils {

    public static Predicate<SimulatorTimelineEntry<Object>> createContractFilter(Contract contract, String initiateContractEntryId,
                                                                                 String runnerTimeline, LastEntryMessageRetriever messageRetriever, Blue blue) {
        return entry -> {
            boolean timelineMatches = false;
            System.out.println("Worker is checking condition");
            if (contract.getMessaging() != null) {
                timelineMatches = contract.getMessaging().getParticipants().values().stream()
                        .map(Participant::getTimeline)
                        .anyMatch(timeline -> timeline.equals(entry.getTimeline()));

                System.out.println("Timelines: " + contract.getMessaging().getParticipants());
                System.out.println("timelineMatches=" + timelineMatches);
            }

            boolean threadMatches = initiateContractEntryId.equals(entry.getThread());
            System.out.println("threadMatches=" + threadMatches);

            if (timelineMatches && threadMatches)
                return true;

            if (entry.getMessage() instanceof ContractUpdateAction) {
                ContractUpdateAction theirAction = (ContractUpdateAction) entry.getMessage();

                ContractUpdateAction ourLastAction = messageRetriever.getMessageFromLastTimelineEntry(runnerTimeline, ContractUpdateAction.class);
                ContractInstance contractInstance = ourLastAction.getContractInstance();

                Set<String> initiateContractEntries = new HashSet<>();

                if (contractInstance.getContractState() != null) {
                    GenericContract mainContractInstance = blue.nodeToObject(contractInstance.getContractState(), GenericContract.class);
                    if (mainContractInstance.getSubscriptions() != null) {
                        mainContractInstance.getSubscriptions().stream()
                                .filter(AllEventsExternalContractSubscription.class::isInstance)
                                .map(AllEventsExternalContractSubscription.class::cast)
                                .map(AllEventsExternalContractSubscription::getInitiateContractEntry)
                                .forEach(initiateContractEntries::add);
                    }
                }

                if (contractInstance.getProcessingState() != null && contractInstance.getProcessingState().getLocalContractInstances() != null) {
                    contractInstance.getProcessingState().getLocalContractInstances().stream()
                            .filter(localInstance -> localInstance.getContractState() != null)
                            .map(localInstance -> blue.nodeToObject(localInstance.getContractState(), GenericContract.class))
                            .filter(localInstance -> localInstance.getSubscriptions() != null)
                            .flatMap(localInstance -> localInstance.getSubscriptions().stream())
                            .filter(AllEventsExternalContractSubscription.class::isInstance)
                            .map(AllEventsExternalContractSubscription.class::cast)
                            .map(AllEventsExternalContractSubscription::getInitiateContractEntry)
                            .forEach(initiateContractEntries::add);
                }

                return initiateContractEntries.contains(theirAction.getInitiateContractEntry());
            }

            return false;

        };
    }

    public static Predicate<SimulatorTimelineEntry<Object>> createContractFilterForSimulator(Contract contract, String initiateContractEntryId,
                                                                                             String runnerTimeline, Simulator simulator, Blue blue) {
        LastEntryMessageRetriever retriever = simulator::getMessageFromLastTimelineEntry;
        return createContractFilter(contract, initiateContractEntryId, runnerTimeline, retriever, blue);
    }

    public static Predicate<SimulatorTimelineEntry<Object>> createContractFilterForSimulatorMT(Contract contract, String initiateContractEntryId,
                                                                                               String runnerTimeline, SimulatorMT simulatorMT, Blue blue) {
        LastEntryMessageRetriever retriever = simulatorMT::getMessageFromLastTimelineEntry;
        return createContractFilter(contract, initiateContractEntryId, runnerTimeline, retriever, blue);
    }

}
