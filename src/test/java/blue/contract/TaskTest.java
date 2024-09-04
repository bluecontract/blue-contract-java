package blue.contract;

import blue.contract.model.ContractUpdateAction;
import blue.contract.model.TimelineEntry;
import blue.contract.model.action.InitiateContractAction;
import blue.contract.model.action.InitiateContractProcessingAction;
import blue.contract.model.blink.AssistantMessage;
import blue.contract.model.blink.MainChatMessage;
import blue.contract.model.blink.SampleTask;
import blue.contract.model.blink.Task;
import blue.contract.model.event.WorkflowInstanceStartedEvent;
import blue.contract.packager.model.BluePackage;
import blue.contract.simulator.ContractRunner;
import blue.contract.simulator.Simulator;
import blue.contract.simulator.model.SimulatorTimelineEntry;
import blue.contract.simulator.utils.ContractRunnerSubscriptionUtils;
import blue.contract.utils.PackagingUtils;
import blue.contract.utils.RepositoryExportingTool;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.provider.DirectoryBasedNodeProvider;
import blue.language.utils.NodeToMapListOrValue;
import blue.language.utils.TypeClassResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_1;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_2;
import static blue.contract.utils.Utils.defaultTestingEnvironment;
import static blue.contract.utils.Utils.testBlue;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class TaskTest {

    private Simulator simulator;
    private Blue blue;

    @BeforeEach
    void setUp() throws IOException {
        blue = testBlue();
        simulator = new Simulator(blue);
    }

    @Test
    void testInitializationEvent() throws IOException {
        Node contract = YAML_MAPPER.readValue(new File("src/main/resources/blue-preprocessed/Blink/SampleTask.blue"), Node.class);
        blue.resolve(contract);

        Task task = blue.nodeToObject(contract, Task.class);
        System.out.println(task.getProperties().getConversation().get(0));
        task.getProperties().getConversation().add(new AssistantMessage());

    }


    @Test
    void testDirectTimelineEntries() throws IOException {
        SampleTask task = new SampleTask("assistant-id");
        System.out.println(blue.nodeToYaml(blue.resolve(blue.objectToNode(task))));

        ContractRunner simulator = new ContractRunner(blue, SAMPLE_BLUE_ID_1, SAMPLE_BLUE_ID_2);

        simulator.initiateContract(task);
        simulator.processEvents();

        simulator.save("src/test/resources", "sample");
    }


    @Test
    void testDirectTimelineEntries2() throws IOException {
        String alice = "Alice";
        String aliceTimeline = simulator.createTimeline(alice);

        String blink = "Blink";
        String blinkTimeline = simulator.createTimeline(blink);

        Task task = new Task(aliceTimeline, blinkTimeline);
        InitiateContractAction initiateChessAction = new InitiateContractAction(task);
        String initiateContractEntry = simulator.appendEntry(aliceTimeline, initiateChessAction);

        String runner = "Contract Runner";
        String runnerTimeline = simulator.createTimeline(runner);

        InitiateContractProcessingAction initiateProcessingAction = new InitiateContractProcessingAction(initiateContractEntry, task);
        String initiateContractProcessingEntry = simulator.appendEntry(runnerTimeline, initiateProcessingAction);

        ContractRunner contractRunner = new ContractRunner(blue, initiateContractEntry, initiateContractProcessingEntry);
        contractRunner.startProcessingContract(task, runnerTimeline, simulator);

        simulator.subscribe(
                entry -> runnerTimeline.equals(entry.getTimeline()),
                entry -> {
                    ContractUpdateAction updateAction = (ContractUpdateAction) entry.getMessage();
                    if (updateAction.getEmittedEvents() != null) {
                        for (Node event : updateAction.getEmittedEvents()) {
                            Optional<Class<?>> clazz = blue.determineClass(event);
                            if (clazz.isPresent() && clazz.get() == WorkflowInstanceStartedEvent.class) {
                                WorkflowInstanceStartedEvent instanceStartedEvent = blue.nodeToObject(event, WorkflowInstanceStartedEvent.class);
                                if ("BlinkResponse".equals(instanceStartedEvent.getCurrentStepName())) {
//                                    SimulatorTimelineEntry userEntry = blue.nodeToObject(updateAction.getIncomingEvent(), SimulatorTimelineEntry.class);
                                    String userEntry = updateAction.getIncomingEvent().getAsText("/message/value");
                                    MainChatMessage chatMessage = new MainChatMessage()
                                            .responseTo(userEntry)
                                            .message("Yes, " + userEntry + ", right");
                                    simulator.appendEntry(blinkTimeline, initiateContractEntry, chatMessage);
                                }
                            }
                        }
                    }
                });

        simulator.appendEntry(aliceTimeline, initiateContractEntry, "abc123");
        simulator.appendEntry(aliceTimeline, initiateContractEntry, "XXX");

        contractRunner.save("src/test/resources", "sample");
    }

}
