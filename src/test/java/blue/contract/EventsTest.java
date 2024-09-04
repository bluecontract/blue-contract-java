package blue.contract;

import blue.contract.model.ContractUpdateAction;
import blue.contract.model.event.ContractProcessingEvent;
import blue.contract.model.event.ContractUpdateEvent;
import blue.contract.packager.model.BluePackage;
import blue.contract.simulator.ContractRunner;
import blue.contract.utils.PackagingUtils.ClasspathBasedPackagingEnvironment;
import blue.contract.utils.Utils;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.provider.DirectoryBasedNodeProvider;
import blue.language.utils.TypeClassResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static blue.contract.utils.PackagingUtils.createClasspathBasedPackagingEnvironment;
import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_1;
import static blue.contract.utils.SampleBlueIds.SAMPLE_BLUE_ID_2;
import static blue.contract.utils.Utils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventsTest {

    @Test
    void testInitializationEvent() throws IOException {
        Blue blue = testBlue();
        Node contract = getPreprocessedNode(TESTING_EVENTS, "Initialization");

        ContractRunner simulator = new ContractRunner(blue, SAMPLE_BLUE_ID_1, SAMPLE_BLUE_ID_2);

        simulator.initiateContract(contract);
        simulator.processEmittedEventsOnly();

        ContractUpdateAction initial = simulator.getContractUpdates().get(0);

        String contractProcessingEventBlueId = getPreprocessedNode(BLUE_CONTRACTS_V04, "Contract Processing Event").getAsText("/blueId");
        String contractUpdateEventBlueId = getPreprocessedNode(BLUE_CONTRACTS_V04, "Contract Update Event").getAsText("/blueId");

        assertEquals(1, initial.getEmittedEvents().size());

        Node event = initial.getEmittedEvents().get(0);
        assertEquals(contractProcessingEventBlueId, event.getType().getAsText("/blueId"));

        ContractProcessingEvent contractProcessingEvent = blue.nodeToObject(event, ContractProcessingEvent.class);

        assertEquals(0, contractProcessingEvent.getContractInstanceId());
        assertEquals(0, contractProcessingEvent.getWorkflowInstanceId());
        assertEquals(SAMPLE_BLUE_ID_1, contractProcessingEvent.getInitiateContractEntry().getBlueId());
        assertEquals(SAMPLE_BLUE_ID_2, contractProcessingEvent.getInitiateContractProcessingEntry().getBlueId());

        Node updateEventNode = contractProcessingEvent.getEvent();
        assertEquals(contractUpdateEventBlueId, updateEventNode.getType().get("/blueId"));

        ContractUpdateEvent contractUpdateEvent = blue.nodeToObject(updateEventNode, ContractUpdateEvent.class);
        Node changeset = contractUpdateEvent.getChangeset();
        assertEquals(1, changeset.getItems().size());

        Node firstChange = changeset.getItems().get(0);
        assertEquals("1x", firstChange.getAsText("/val"));
        assertEquals("replace", firstChange.getAsText("/op"));
        assertEquals("/properties/x", firstChange.getAsText("/path"));
    }



}