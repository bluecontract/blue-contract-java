package blue.contract.simulator.utils;

import blue.contract.model.Contract;
import blue.contract.model.Participant;
import blue.contract.simulator.model.SimulatorTimelineEntry;

import java.util.function.Predicate;

public class ContractRunnerSubscriptionUtils {

    public static Predicate<SimulatorTimelineEntry<Object>> createContractFilter(Contract contract, String initiateContractEntryId) {
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

            return timelineMatches && threadMatches;
        };
    }

}
