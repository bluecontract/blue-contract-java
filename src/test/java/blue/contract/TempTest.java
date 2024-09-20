package blue.contract;

import blue.contract.model.GenericContract;
import blue.contract.model.action.InitiateContractAction;
import blue.contract.model.action.InitiateContractProcessingAction;
import blue.contract.model.testcontract.TempSample;
import blue.contract.simulator.ContractRunner2;
import blue.contract.simulator.Simulator;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.MergeReverser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static blue.contract.utils.Utils.testBlue;
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

        GenericContract genericContract = blue.nodeToObject(contractRunner.getLastContractUpdate().getContractInstance().getContractState(), GenericContract.class);
        System.out.println("x=" + genericContract.getProperties().get("x").getValue());

        System.out.println(blue.nodeToYaml(contractRunner.getLastContractUpdate().getContractInstance().getContractState()));

        Node rev = new MergeReverser().reverse(contractRunner.getLastContractUpdate().getContractInstance().getContractState());
        System.out.println(blue.nodeToYaml(rev));
    }

}
