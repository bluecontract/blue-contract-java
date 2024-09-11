package blue.contract;

import blue.contract.model.action.InitiateContractAction;
import blue.contract.model.action.InitiateContractProcessingAction;
import blue.contract.model.blink.*;
import blue.contract.model.testcontract.SampleAPIContract;
import blue.contract.simulator.*;
import blue.contract.simulator.processor.APIRequestProcessor;
import blue.contract.simulator.processor.LLMRequestProcessor;
import blue.language.Blue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static blue.contract.utils.Utils.testBlue;

public class SampleAPITest {

    private Simulator simulator;
    private Blue blue;

    @BeforeEach
    void setUp() throws IOException {
        blue = testBlue();
        simulator = new Simulator(blue);
    }

    void testInitializationEvent() throws IOException {
        String assistantId = "Assistant";
        String assistantTimeline = simulator.createTimeline(assistantId);

        SampleAPIContract contract = new SampleAPIContract(assistantTimeline);
        InitiateContractAction initiateContractAction = new InitiateContractAction(contract);
        String initiateContractEntry = simulator.appendEntry(assistantTimeline, initiateContractAction);

        String runner = "Contract Runner";
        String runnerTimeline = simulator.createTimeline(runner);

        InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(initiateContractEntry, contract);
        String initiateContractProcessingEntry = simulator.appendEntry(runnerTimeline, initiateProcessingAction);

        Assistant assistant = new Assistant(blue, initiateContractEntry);
        assistant.registerProcessor(APIRequest.class, APIResponse.class, new APIRequestProcessor());
        assistant.registerProcessor(LLMRequest.class, LLMResponse.class, new LLMRequestProcessor());
        assistant.start(assistantTimeline, runnerTimeline, simulator);

        ContractRunner2 contractRunner = new ContractRunner2(blue, initiateContractEntry, initiateContractProcessingEntry);
        contractRunner.startProcessingContract(contract, runnerTimeline, simulator);

//        System.out.println(blue.objectToSimpleYaml(contractRunner.getLastContractUpdate().getContractInstance().getContractState()));
//        contractRunner.save("src/test/resources", "api");
    }

    @Test
    void testTask() throws IOException {
        String aliceId = "Alice";
        String aliceTimeline = simulator.createTimeline(aliceId);

        String assistantId = "Assistant";
        String assistantTimeline = simulator.createTimeline(assistantId);

        Task taskContract = new Task(aliceTimeline, assistantTimeline);
        InitiateContractAction initiateContractAction = new InitiateContractAction(taskContract);
        String initiateContractEntry = simulator.appendEntry(assistantTimeline, initiateContractAction);

        String taskRunnerId = "Task Contract Runner";
        String taskRunnerTimeline = simulator.createTimeline(taskRunnerId);

        InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(initiateContractEntry, taskContract);
        String initiateContractProcessingEntry = simulator.appendEntry(taskRunnerTimeline, initiateProcessingAction);

        Assistant assistant = new Assistant(blue, initiateContractEntry);
        assistant.registerProcessor(APIRequest.class, APIResponse.class, new APIRequestProcessor());
        assistant.registerProcessor(LLMRequest.class, LLMResponse.class, new LLMRequestProcessor());
        assistant.start(assistantTimeline, taskRunnerTimeline, simulator);

        ContractRunner2 taskRunner = new ContractRunner2(blue, initiateContractEntry, initiateContractProcessingEntry);
        taskRunner.startProcessingContract(taskContract, taskRunnerTimeline, simulator);

        SampleTask task = new SampleTask(assistantTimeline, "rn1q1rk1/pp2b1pp/2p2n2/3p1pB1/3P4/1QP2N2/PP1N1PPP/R4RK1 b - - 1 11");
        simulator.appendEntry(aliceTimeline, initiateContractEntry, blue.resolve(blue.objectToNode(task)));
        task = new SampleTask(assistantTimeline, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        simulator.appendEntry(aliceTimeline, initiateContractEntry, blue.resolve(blue.objectToNode(task)));

        simulator.save(taskRunnerTimeline, 2, "src/test/resources", "task");

    }

    void testTaskMT() throws IOException, InterruptedException {
        SimulatorMT simulator = new SimulatorMT(blue);

        String aliceId = "Alice";
        String aliceTimeline = simulator.createTimeline(aliceId);

        String assistantId = "Assistant";
        String assistantTimeline = simulator.createTimeline(assistantId);

        Task taskContract = new Task(aliceTimeline, assistantTimeline);
        InitiateContractAction initiateContractAction = new InitiateContractAction(taskContract);
        String initiateContractEntry = simulator.appendEntry(assistantTimeline, initiateContractAction);

        String taskRunnerId = "Task Contract Runner";
        String taskRunnerTimeline = simulator.createTimeline(taskRunnerId);

        InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(initiateContractEntry, taskContract);
        String initiateContractProcessingEntry = simulator.appendEntry(taskRunnerTimeline, initiateProcessingAction);

        AssistantMT assistant = new AssistantMT(blue, initiateContractEntry);
        assistant.registerProcessor(APIRequest.class, APIResponse.class, new APIRequestProcessor());
        assistant.registerProcessor(LLMRequest.class, LLMResponse.class, new LLMRequestProcessor());
        assistant.start(assistantTimeline, taskRunnerTimeline, simulator);

        ContractRunnerMT taskRunner = new ContractRunnerMT(blue, initiateContractEntry, initiateContractProcessingEntry);
        taskRunner.startProcessingContract(taskContract, taskRunnerTimeline, simulator);

        SampleTask task = new SampleTask(assistantTimeline, "rn1q1rk1/pp2b1pp/2p2n2/3p1pB1/3P4/1QP2N2/PP1N1PPP/R4RK1 b - - 1 11");
        simulator.appendEntry(aliceTimeline, initiateContractEntry, blue.resolve(blue.objectToNode(task)));
//
//        // Allow some time for processing (adjust as needed)
        Thread.sleep(10000);
//
        // Stop processing
        assistant.stop();
        taskRunner.stop();

        // Shutdown the simulator
        simulator.shutdown();
    }

}
