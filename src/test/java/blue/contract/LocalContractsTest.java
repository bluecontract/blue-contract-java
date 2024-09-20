package blue.contract;

import blue.contract.model.GenericContract;
import blue.contract.model.action.InitiateContractAction;
import blue.contract.model.action.InitiateContractProcessingAction;
import blue.contract.model.testcontract.LocalContractsContract;
import blue.contract.model.testcontract.LocalSubscriptionContract;
import blue.contract.simulator.ContractRunner2;
import blue.contract.simulator.Simulator;
import blue.language.Blue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static blue.contract.utils.Utils.testBlue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocalContractsTest {

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

        LocalContractsContract contract = new LocalContractsContract();
        InitiateContractAction initiateContractAction = new InitiateContractAction(contract);
        String initiateContractEntry = simulator.appendEntry(aliceTimeline, initiateContractAction);

        String runner = "Contract Runner";
        String runnerTimeline = simulator.createTimeline(runner);

        InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(initiateContractEntry, contract);
        String initiateContractProcessingEntry = simulator.appendEntry(runnerTimeline, initiateProcessingAction);

        ContractRunner2 contractRunner = new ContractRunner2(blue, initiateContractEntry, initiateContractProcessingEntry);
        contractRunner.startProcessingContract(contract, runnerTimeline, simulator);

        GenericContract genericContract = blue.nodeToObject(contractRunner.getLastContractUpdate().getContractInstance().getProcessingState().getLocalContractInstances().get(0).getContractState(), GenericContract.class);
        assertEquals("main-contract-participant-id", genericContract.getMessaging().getParticipants().get("Alice").getTimeline());

//        contractRunner.save("src/test/resources", "local");
    }
    
}
