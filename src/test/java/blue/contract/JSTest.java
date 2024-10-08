package blue.contract;

import blue.contract.model.GenericContract;
import blue.contract.model.action.InitiateContractAction;
import blue.contract.model.action.InitiateContractProcessingAction;
import blue.contract.simulator.ContractRunner2;
import blue.contract.simulator.Simulator;
import blue.language.Blue;
import blue.language.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static blue.contract.utils.Utils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JSTest {

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

                Blue blue = testBlue();
                Node contractNode = getPreprocessedNode(TESTING_JS, "JS Engine Test Contract");

                GenericContract contract = blue.nodeToObject(contractNode, GenericContract.class);
                InitiateContractAction initiateContractAction = new InitiateContractAction(contract);
                String initiateContractEntry = simulator.appendEntry(aliceTimeline, initiateContractAction);

                String runner = "Contract Runner";
                String runnerTimeline = simulator.createTimeline(runner);

                InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(
                                initiateContractEntry, contract);
                String initiateContractProcessingEntry = simulator.appendEntry(runnerTimeline,
                                initiateProcessingAction);

                ContractRunner2 contractRunner = new ContractRunner2(blue, initiateContractEntry,
                                initiateContractProcessingEntry);
                contractRunner.startProcessingContract(contract, runnerTimeline, simulator);

                GenericContract genericContract = blue.nodeToObject(
                                contractRunner.getLastContractUpdate().getContractInstance().getContractState(),
                                GenericContract.class);

                simulator.save(runnerTimeline, 2, "src/test/resources", "js_test");

                assertNotNull(genericContract.getProperties().get("testMainCompleted"),
                                "testMainCompleted property should not be null");
                assertEquals(true, genericContract.getProperties().get("testMainCompleted").getValue());

                assertNotNull(genericContract.getProperties().get("testExpressionsCompleted"),
                                "testExpressionsCompleted property should not be null");
                assertEquals(true, genericContract.getProperties().get("testExpressionsCompleted").getValue());
        }

}