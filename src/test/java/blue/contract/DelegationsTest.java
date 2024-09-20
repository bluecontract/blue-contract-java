package blue.contract;

import blue.contract.model.GenericContract;
import blue.contract.model.action.InitiateContractAction;
import blue.contract.model.action.InitiateContractProcessingAction;
import blue.contract.model.testcontract.DelegationTestingContract;
import blue.contract.model.testcontract.LocalSubscriptionContract;
import blue.contract.simulator.ContractRunner2;
import blue.contract.simulator.Simulator;
import blue.language.Blue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;

import static blue.contract.utils.Utils.testBlue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DelegationsTest {

    private Simulator simulator;
    private Blue blue;

    @BeforeEach
    void setUp() throws IOException {
        blue = testBlue();
        simulator = new Simulator(blue);
    }

    @Test
    void testDelegation() throws IOException {
        String alice = "Alice";
        String aliceTimeline = simulator.createTimeline(alice);

        String bob = "Bob";
        String bobTimeline = simulator.createTimeline(bob);

        DelegationTestingContract contract = new DelegationTestingContract(aliceTimeline, bobTimeline);
        InitiateContractAction initiateContractAction = new InitiateContractAction(contract);
        String initiateContractEntry = simulator.appendEntry(aliceTimeline, initiateContractAction);

        String runner = "Contract Runner";
        String runnerTimeline = simulator.createTimeline(runner);

        InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(initiateContractEntry, contract);
        String initiateContractProcessingEntry = simulator.appendEntry(runnerTimeline, initiateProcessingAction);

        ContractRunner2 contractRunner = new ContractRunner2(blue, initiateContractEntry, initiateContractProcessingEntry);
        contractRunner.startProcessingContract(contract, runnerTimeline, simulator);

//        simulator.appendEntry(aliceTimeline, initiateContractEntry, "abc");
//        simulator.appendEntry(bobTimeline, initiateContractEntry, "abc");
        simulator.appendEntry(bobTimeline, initiateContractEntry, BigInteger.ONE);

        GenericContract genericContract = blue.nodeToObject(contractRunner.getLastContractUpdate().getContractInstance().getContractState(), GenericContract.class);
        System.out.println("x = " + genericContract.getProperties().get("x").getValue());
    }
    
}
