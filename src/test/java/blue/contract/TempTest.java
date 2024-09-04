package blue.contract;

import blue.contract.model.ContractUpdateAction;
import blue.contract.model.action.InitiateContractAction;
import blue.contract.model.action.InitiateContractProcessingAction;
import blue.contract.model.blink.AssistantMessage;
import blue.contract.model.blink.MainChatMessage;
import blue.contract.model.blink.SampleTask;
import blue.contract.model.blink.Task;
import blue.contract.model.event.WorkflowInstanceStartedEvent;
import blue.contract.model.testcontract.LocalSubscriptionContract;
import blue.contract.model.testcontract.TempSample;
import blue.contract.simulator.ContractRunner;
import blue.contract.simulator.ContractRunner2;
import blue.contract.simulator.Simulator;
import blue.language.Blue;
import blue.language.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_1;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_2;
import static blue.contract.utils.Utils.testBlue;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TempTest {

    private Simulator simulator;
    private Blue blue;

    @BeforeEach
    void setUp() throws IOException {
        blue = testBlue();
        simulator = new Simulator(blue);
    }

    @Test
    void testInitializationEvent() throws IOException {
        String alice = "Alice";
        String aliceTimeline = simulator.createTimeline(alice);

        String bob = "Bob";
        String bobTimeline = simulator.createTimeline(bob);

        TempSample contract = new TempSample(aliceTimeline, bobTimeline);
        InitiateContractAction initiateContractAction = new InitiateContractAction(contract);
        String initiateContractEntry = simulator.appendEntry(aliceTimeline, initiateContractAction);

        String runner = "Contract Runner";
        String runnerTimeline = simulator.createTimeline(runner);

        InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(initiateContractEntry, contract);
        String initiateContractProcessingEntry = simulator.appendEntry(runnerTimeline, initiateProcessingAction);

        ContractRunner2 contractRunner = new ContractRunner2(blue, initiateContractEntry, initiateContractProcessingEntry);
        contractRunner.startProcessingContract(contract, runnerTimeline, simulator);

        simulator.appendEntry(bobTimeline, initiateContractEntry, "xyz");

        System.out.println("x=" + contractRunner.getLastContractUpdate().getContractInstance().getContractState().getProperties().get("x").getValue());
    }

}
