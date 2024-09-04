package blue.contract;

import blue.contract.model.ContractUpdateAction;
import blue.contract.model.action.InitiateContractAction;
import blue.contract.model.action.InitiateContractProcessingAction;
import blue.contract.model.chess.Chess;
import blue.contract.model.testcontract.LocalSubscriptionContract;
import blue.contract.simulator.ContractRunner;
import blue.contract.simulator.ContractRunner2;
import blue.contract.simulator.Simulator;
import blue.language.Blue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_1;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_2;
import static blue.contract.utils.Utils.testBlue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocalSubscriptionTest {

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

        LocalSubscriptionContract contract = new LocalSubscriptionContract();
        InitiateContractAction initiateContractAction = new InitiateContractAction(contract);
        String initiateContractEntry = simulator.appendEntry(aliceTimeline, initiateContractAction);

        String runner = "Contract Runner";
        String runnerTimeline = simulator.createTimeline(runner);

        InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(initiateContractEntry, contract);
        String initiateContractProcessingEntry = simulator.appendEntry(runnerTimeline, initiateProcessingAction);

        ContractRunner2 contractRunner = new ContractRunner2(blue, initiateContractEntry, initiateContractProcessingEntry);
        contractRunner.startProcessingContract(contract, runnerTimeline, simulator);

        assertEquals(2, contractRunner.getContractUpdates().size());
    }
    
}
