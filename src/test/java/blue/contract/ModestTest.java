package blue.contract;

import blue.contract.model.AssistantTask;
import blue.contract.model.ContractInstance;
import blue.contract.model.ContractUpdateAction;
import blue.contract.model.TimelineEntry;
import blue.contract.model.action.InitiateContractAction;
import blue.contract.model.action.InitiateContractProcessingAction;
import blue.contract.model.blink.*;
import blue.contract.model.testcontract.SampleAPIContract;
import blue.contract.processor.StandardProcessorsProvider;
import blue.contract.simulator.*;
import blue.contract.simulator.processor.APIRequestProcessor;
import blue.contract.simulator.processor.LLMRequestProcessor;
import blue.contract.utils.anthropic.AnthropicConfig;
import blue.contract.utils.anthropic.AnthropicKey;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.NodeToMapListOrValue;
import blue.language.utils.UncheckedObjectMapper;
import blue.language.utils.limits.Limits;

import org.gradle.internal.impldep.org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import static blue.contract.utils.Utils.testBlue;
import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class ModestTest {

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
        assistant.registerProcessor(LLMRequest.class, LLMResponse.class, new LLMRequestProcessor(new AnthropicConfig(AnthropicKey.ANTHROPIC_KEY)));
        assistant.start(assistantTimeline, runnerTimeline, simulator);

        ContractRunner2 contractRunner = new ContractRunner2(blue, initiateContractEntry, initiateContractProcessingEntry);
        contractRunner.startProcessingContract(contract, runnerTimeline, simulator);

//        System.out.println(blue.objectToSimpleYaml(contractRunner.getLastContractUpdate().getContractInstance().getContractState()));
//        contractRunner.save("src/test/resources", "api");
    }

    @Test
    void testTypes() throws StreamWriteException, DatabindException, IOException {
        Node expectedEvent = UncheckedObjectMapper.JSON_MAPPER.convertValue(
            UncheckedObjectMapper.YAML_MAPPER.readValue(new FileInputStream("src/test/resources/debug/eng_expected_event.yml"), Object.class),
Node.class
        );
        Node event = UncheckedObjectMapper.JSON_MAPPER.convertValue(
            UncheckedObjectMapper.YAML_MAPPER.readValue(new FileInputStream("src/test/resources/debug/eng_assistant_entry.yml"), Object.class),
Node.class
        );

        System.out.println(blue.nodeMatchesType(event, expectedEvent));

        blue.extend(event, Limits.NO_LIMITS);
        YAML_MAPPER.writeValue(
            new File("src/test/resources/debug/result/matcher_extended_node.blue"),
            NodeToMapListOrValue.get(event, NodeToMapListOrValue.Strategy.SIMPLE)
        );
    }

    @Test
    void testRaw() throws StreamWriteException, DatabindException, IOException {
        // Object contractInstanceObj = UncheckedObjectMapper.YAML_MAPPER.readValue(new FileInputStream("src/test/resources/debug/" + filePrefix + "_instance.yml"), Object.class);
        // Node contractInstanceNode = UncheckedObjectMapper.JSON_MAPPER.convertValue(contractInstanceObj, Node.class);
        // Object eventObj = UncheckedObjectMapper.YAML_MAPPER.readValue(new FileInputStream("src/test/resources/debug/" + filePrefix + "_event.yml"), Object.class);
        // Node event = UncheckedObjectMapper.JSON_MAPPER.convertValue(eventObj, Node.class);

        Node ccontractUpdateNode = UncheckedObjectMapper.JSON_MAPPER.convertValue(
            UncheckedObjectMapper.YAML_MAPPER.readValue(new FileInputStream("src/test/resources/debug/new/cu.blue"), Object.class),
Node.class
        );
        Node event = UncheckedObjectMapper.JSON_MAPPER.convertValue(
            UncheckedObjectMapper.YAML_MAPPER.readValue(new FileInputStream("src/test/resources/debug/new/event.blue"), Object.class),
Node.class
        );
        ContractUpdateAction contractUpdate  = blue.nodeToObject(ccontractUpdateNode, ContractUpdateAction.class);

        //ContractInstance contractInstance = blue.nodeToObject(contractInstanceNode, ContractInstance.class);
        String initiateContractEntryBlueId = "4iZmiJ2sfFNnLxVBM7EGtn72US16C4PMg5DM7eKMny33";
        String initiateContractProcessingEntryBlueId = "Dwn46atirFxdMCexeqqkMi5EwsQWa2fEBCEHP8m3sKo3";

        ContractProcessor processor = new ContractProcessor(new StandardProcessorsProvider(blue), blue);
        List<ContractUpdateAction> result = processor.processEvent(
            event, contractUpdate.getContractInstance(), initiateContractEntryBlueId, initiateContractProcessingEntryBlueId, 1
        );
        System.out.println("Results:");
        System.out.println(result.get(0));

        File outputFile = new File("src/test/resources/debug/new/result.blue");
        YAML_MAPPER.writeValue(outputFile, NodeToMapListOrValue.get(blue.objectToNode(result.get(0)), NodeToMapListOrValue.Strategy.SIMPLE));
    }

    //@Test
    void testAssistantRaw() throws StreamWriteException, DatabindException, IOException {
        String filePrefix = "api"; // api | eng
        Node event = UncheckedObjectMapper.JSON_MAPPER.convertValue(
            UncheckedObjectMapper.YAML_MAPPER.readValue(new FileInputStream("src/test/resources/debug/" + filePrefix + "_task_entry.yml"), Object.class),
Node.class
        );
        TimelineEntry entry = blue.nodeToObject(event, TimelineEntry.class);
        Assistant assistant = new Assistant(blue, "", "HY2sws4D2cT67tpxQXMbV5u3F9zRMU3hDsqKLhY3qkur");
        assistant.registerProcessor(APIRequest.class, APIResponse.class, new APIRequestProcessor());
        assistant.registerProcessor(LLMRequest.class, LLMResponse.class, new LLMRequestProcessor(new AnthropicConfig(AnthropicKey.ANTHROPIC_KEY)));
        List<AssistantTask> tasks = assistant.processContractUpdateAction(entry);

        File outputFile = new File("src/test/resources/debug/result/" + filePrefix + "_task_result.yml");
        if (tasks.size() > 0) {
            YAML_MAPPER.writeValue(outputFile, NodeToMapListOrValue.get(blue.objectToNode(tasks.get(0)), NodeToMapListOrValue.Strategy.SIMPLE));
        }
    }

    @Test
    void testTask() throws IOException {
        String aliceId = "Alice";
        String aliceTimeline = simulator.createTimeline(aliceId);

        String assistantId = "Assistant";
        String assistantTimeline = simulator.createTimeline(assistantId);

        Task taskContract = new Task(aliceTimeline, assistantTimeline);
        // Node  contractNode = JSON_MAPPER.convertValue(taskContract, Node.class);
        // File outputFile = new File("src/test/resources/_test_task_contract.blue");
        // YAML_MAPPER.writeValue(outputFile, NodeToMapListOrValue.get(contractNode, NodeToMapListOrValue.Strategy.SIMPLE));

        InitiateContractAction initiateContractAction = new InitiateContractAction(taskContract);
        String initiateContractEntry = simulator.appendEntry(assistantTimeline, initiateContractAction);

        String taskRunnerId = "Task Contract Runner";
        String taskRunnerTimeline = simulator.createTimeline(taskRunnerId);

        InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(initiateContractEntry, taskContract);
        String initiateContractProcessingEntry = simulator.appendEntry(taskRunnerTimeline, initiateProcessingAction);

        Assistant assistant = new Assistant(blue, initiateContractEntry);
        assistant.registerProcessor(APIRequest.class, APIResponse.class, new APIRequestProcessor());
        assistant.registerProcessor(LLMRequest.class, LLMResponse.class, new LLMRequestProcessor(new AnthropicConfig(AnthropicKey.ANTHROPIC_KEY)));
        assistant.start(assistantTimeline, taskRunnerTimeline, simulator);

        ContractRunner2 taskRunner = new ContractRunner2(blue, initiateContractEntry, initiateContractProcessingEntry);
        taskRunner.startProcessingContract(taskContract, taskRunnerTimeline, simulator);

        // SampleTask task = new SampleTask(assistantTimeline, "rn1q1rk1/pp2b1pp/2p2n2/3p1pB1/3P4/1QP2N2/PP1N1PPP/R4RK1 b - - 1 11");        
        // simulator.appendEntry(aliceTimeline, initiateContractEntry, blue.resolve(blue.objectToNode(task)));

        SampleTask task = new SampleTask(assistantTimeline, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        simulator.appendEntry(aliceTimeline, initiateContractEntry, blue.resolve(blue.objectToNode(task)));

        simulator.save(taskRunnerTimeline, 0, "src/test/resources", "task_debug");

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
        assistant.registerProcessor(LLMRequest.class, LLMResponse.class, new LLMRequestProcessor(new AnthropicConfig(AnthropicKey.ANTHROPIC_KEY)));
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
