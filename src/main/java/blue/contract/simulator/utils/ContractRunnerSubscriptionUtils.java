package blue.contract.simulator.utils;

import blue.contract.model.Contract;
import blue.contract.model.ContractInstance;
import blue.contract.model.ContractUpdateAction;
import blue.contract.model.Participant;
import blue.contract.model.subscription.AllEventsExternalContractSubscription;
import blue.contract.simulator.Simulator;
import blue.contract.simulator.SimulatorMT;
import blue.contract.model.blink.SimulatorTimelineEntry;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class ContractRunnerSubscriptionUtils {

    public static Predicate<SimulatorTimelineEntry<Object>> createContractFilter(Contract contract, String initiateContractEntryId,
                                                                                 String runnerTimeline, Simulator simulator) {
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

                ContractUpdateAction ourLastAction = simulator.getMessageFromLastTimelineEntry(runnerTimeline, ContractUpdateAction.class);
                ContractInstance contractInstance = ourLastAction.getContractInstance();

                Set<String> initiateContractEntries = new HashSet<>();

                if (contractInstance.getContractState() != null && contractInstance.getContractState().getSubscriptions() != null) {
                    contractInstance.getContractState().getSubscriptions().stream()
                            .filter(AllEventsExternalContractSubscription.class::isInstance)
                            .map(AllEventsExternalContractSubscription.class::cast)
                            .map(AllEventsExternalContractSubscription::getInitiateContractEntry)
                            .forEach(initiateContractEntries::add);
                }

                if (contractInstance.getProcessingState() != null && contractInstance.getProcessingState().getLocalContractInstances() != null) {
                    contractInstance.getProcessingState().getLocalContractInstances().stream()
                            .filter(localInstance -> localInstance.getContractState() != null && localInstance.getContractState().getSubscriptions() != null)
                            .flatMap(localInstance -> localInstance.getContractState().getSubscriptions().stream())
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

    public static Predicate<SimulatorTimelineEntry<Object>> createContractFilter(Contract contract, String initiateContractEntryId,
                                                                                 String runnerTimeline, SimulatorMT simulator) {
        throw new RuntimeException("Not implemented");
    }

}
