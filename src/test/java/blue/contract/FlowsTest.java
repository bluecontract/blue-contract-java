package blue.contract;

import blue.contract.model.GenericContract;
import blue.contract.packager.model.BluePackage;
import blue.contract.simulator.ContractRunner;
import blue.contract.utils.PackagingUtils.ClasspathBasedPackagingEnvironment;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.provider.DirectoryBasedNodeProvider;
import blue.language.utils.TypeClassResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static blue.contract.utils.PackagingUtils.createClasspathBasedPackagingEnvironment;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_1;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_2;
import static blue.contract.utils.Utils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FlowsTest {

    @Test
    void testInitializationEvent() throws IOException {
        Blue blue = testBlue();
        Node contract = getPreprocessedNode(TESTING_FLOWS, "JS Complete Contract");

        GenericContract contract1 = blue.nodeToObject(contract, GenericContract.class);
        System.out.println(blue.objectToYaml(contract1));

        ContractRunner simulator = new ContractRunner(blue, SAMPLE_BLUE_ID_1, SAMPLE_BLUE_ID_2);
//
//        simulator.initiateContract(contract);
////        simulator.processEmittedEventsOnly();
//
//        simulator.save("src/test/resources", "f1");


//
//        ContractUpdate initial = simulator.getContractUpdates().get(0);
//
//        BluePackage contractsPackage = env.getPackage(BLUE_CONTRACTS_V04);
//        String contractProcessingEventBlueId = contractsPackage.getPreprocessedNodes().get("Contract Processing Event").getAsText("/blueId");
//        String contractUpdateEventBlueId = contractsPackage.getPreprocessedNodes().get("Contract Update Event").getAsText("/blueId");
//
//        assertEquals(1, initial.getEmittedEvents().size());
//
//        Node event = initial.getEmittedEvents().get(0);
//        assertEquals(contractProcessingEventBlueId, event.getType().getAsText("/blueId"));
//
//        ContractProcessingEvent contractProcessingEvent = blue.nodeToObject(event, ContractProcessingEvent.class);
//
//        assertEquals(0, contractProcessingEvent.getContractInstanceId());
//        assertEquals(0, contractProcessingEvent.getWorkflowInstanceId());
//        assertEquals(SAMPLE_BLUE_ID_1, contractProcessingEvent.getInitiateContractEntry().getBlueId());
//        assertEquals(SAMPLE_BLUE_ID_2, contractProcessingEvent.getInitiateContractProcessingEntry().getBlueId());
//
//        Node updateEventNode = contractProcessingEvent.getEvent();
//        assertEquals(contractUpdateEventBlueId, updateEventNode.getType().get("/blueId"));
//
//        ContractUpdateEvent contractUpdateEvent = blue.nodeToObject(updateEventNode, ContractUpdateEvent.class);
//        Node changeset = contractUpdateEvent.getChangeset();
//        assertEquals(1, changeset.getItems().size());
//
//        Node firstChange = changeset.getItems().get(0);
//        assertEquals(1, firstChange.getAsInteger("/val"));
//        assertEquals("replace", firstChange.getAsText("/op"));
//        assertEquals("/properties/x", firstChange.getAsText("/path"));
    }

}