package blue.contract;

import blue.contract.model.action.InitiateContractAction;
import blue.contract.model.action.InitiateContractProcessingAction;
import blue.contract.model.blink.*;
import blue.contract.model.testcontract.SampleAPIContract;
import blue.contract.simulator.*;
import blue.contract.simulator.processor.APIRequestProcessor;
import blue.contract.simulator.processor.LLMRequestProcessor;
import blue.language.Blue;
import blue.language.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static blue.contract.utils.Utils.testBlue;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class SampleAPITest {

    private Simulator simulator;
    private Blue blue;

    @BeforeEach
    void setUp() throws IOException {
        blue = testBlue();
        simulator = new Simulator(blue);
    }

    @Test
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
        contractRunner.save("src/test/resources", "api");
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

        Task task = new SampleTask(assistantTimeline);
        simulator.appendEntry(aliceTimeline, initiateContractEntry, blue.resolve(blue.objectToNode(task)));

        taskRunner.save("src/test/resources", "task");

    }

    @Test
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

        Task task = new SampleTask(assistantTimeline);
        simulator.appendEntry(assistantTimeline, initiateContractEntry, blue.resolve(blue.objectToNode(task)));
//
//        // Allow some time for processing (adjust as needed)
        Thread.sleep(10000);
//
        // Stop processing
        assistant.stop();
        taskRunner.stop();

        taskRunner.save("src/test/resources", "task");

        // Shutdown the simulator
        simulator.shutdown();
    }

}
