package blue.contract.simulator;

import blue.contract.simulator.model.InitiateTimelineAction;
import blue.contract.simulator.model.SimulatorTimelineEntry;
import blue.contract.utils.RepositoryExportingTool;
import blue.language.Blue;
import blue.language.provider.DirectoryBasedNodeProvider;
import blue.language.utils.TypeClassResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static blue.contract.utils.Utils.defaultTestingEnvironment;
import static blue.contract.utils.Utils.testBlue;
import static org.junit.jupiter.api.Assertions.*;

class SimulatorTest {

    private Simulator simulator;
    private Blue blue;

    @BeforeEach
    void setUp() throws IOException {
        blue = testBlue();
        simulator = new Simulator(blue);
    }

    @Test
    void testCreateTimeline() {
        String owner = "Alice";
        String timelineId = simulator.createTimeline(owner);

        assertNotNull(timelineId);
        assertEquals(1, simulator.getTimelineCount());

        Map<String, List<SimulatorTimelineEntry<Object>>> timelines = simulator.getTimelines();
        List<SimulatorTimelineEntry<Object>> timeline = timelines.get(timelineId);
        assertEquals(1, timeline.size());

        SimulatorTimelineEntry<Object> entry = timeline.get(0);
        assertEquals(0, entry.getTickSequence());
        assertTrue(entry.getMessage() instanceof InitiateTimelineAction);
        assertEquals(owner, ((InitiateTimelineAction) entry.getMessage()).getOwner());
    }

    @Test
    void testCreateMultipleTimelines() {
        String owner1 = "Alice";
        String owner2 = "Bob";
        String owner3 = "Charlie";

        simulator.createTimeline(owner1);
        simulator.createTimeline(owner2);
        simulator.createTimeline(owner3);

        assertEquals(3, simulator.getTimelineCount());

        Map<String, List<SimulatorTimelineEntry<Object>>> timelines = simulator.getTimelines();
        assertEquals(3, timelines.size());

        for (List<SimulatorTimelineEntry<Object>> timeline : timelines.values()) {
            assertEquals(1, timeline.size());
            SimulatorTimelineEntry<Object> entry = timeline.get(0);
            assertNotNull(entry);
            assertTrue(entry.getTickSequence() >= 0 && entry.getTickSequence() < 3);
            assertTrue(entry.getMessage() instanceof InitiateTimelineAction);
            String owner = ((InitiateTimelineAction) entry.getMessage()).getOwner();
            assertTrue(owner.equals(owner1) || owner.equals(owner2) || owner.equals(owner3));
        }
    }

    @Test
    void testCreateMultipleTimelinesForSameOwner() {
        String owner = "Alice";

        simulator.createTimeline(owner);
        simulator.createTimeline(owner);
        simulator.createTimeline(owner);

        assertEquals(3, simulator.getTimelineCount());

        Map<String, List<SimulatorTimelineEntry<Object>>> timelines = simulator.getTimelines();
        assertEquals(3, timelines.size());

        for (List<SimulatorTimelineEntry<Object>> timeline : timelines.values()) {
            assertEquals(1, timeline.size());
            SimulatorTimelineEntry<Object> entry = timeline.get(0);
            assertNotNull(entry);
            assertTrue(entry.getTickSequence() >= 0 && entry.getTickSequence() < 3);
            assertTrue(entry.getMessage() instanceof InitiateTimelineAction);
            assertEquals(owner, ((InitiateTimelineAction) entry.getMessage()).getOwner());
        }
    }

    @Test
    void testAppendEntry() {
        String owner = "Alice";
        String timelineId = simulator.createTimeline(owner);
        String threadId = "thread1";
        String message = "Hello, World!";

        String entryId = simulator.appendEntry(timelineId, threadId, message);

        assertNotNull(entryId);
        Map<String, List<SimulatorTimelineEntry<Object>>> timelines = simulator.getTimelines();
        List<SimulatorTimelineEntry<Object>> timeline = timelines.get(timelineId);
        assertEquals(2, timeline.size());

        SimulatorTimelineEntry<Object> newEntry = timeline.get(1);
        assertEquals(1, newEntry.getTickSequence());
        assertEquals(timelineId, newEntry.getTimeline());
        assertEquals(threadId, newEntry.getThread());
        assertNull(newEntry.getThreadPrev());
        assertEquals(message, newEntry.getMessage());
    }

    @Test
    void testAppendMultipleEntries() {
        String owner = "Alice";
        String timelineId = simulator.createTimeline(owner);
        String threadId1 = "thread1";
        String threadId2 = "thread2";

        String entryId1 = simulator.appendEntry(timelineId, threadId1, "Message 1");
        String entryId2 = simulator.appendEntry(timelineId, threadId2, "Message 2");
        String entryId3 = simulator.appendEntry(timelineId, threadId1, "Message 3");

        assertNotNull(entryId1);
        assertNotNull(entryId2);
        assertNotNull(entryId3);

        Map<String, List<SimulatorTimelineEntry<Object>>> timelines = simulator.getTimelines();
        List<SimulatorTimelineEntry<Object>> timeline = timelines.get(timelineId);
        assertEquals(4, timeline.size());

        SimulatorTimelineEntry<Object> entry3 = timeline.get(3);
        assertEquals(3, entry3.getTickSequence());
        assertEquals(timelineId, entry3.getTimeline());
        assertEquals(threadId1, entry3.getThread());
        assertEquals(blue.calculateBlueId(timeline.get(1)), entry3.getThreadPrev());
        assertEquals("Message 3", entry3.getMessage());
    }

    @Test
    void testAppendEntryToNonExistentTimeline() {
        String nonExistentTimelineId = "nonexistent";
        String threadId = "thread1";
        String message = "Hello, World!";

        assertThrows(IllegalArgumentException.class, () ->
                simulator.appendEntry(nonExistentTimelineId, threadId, message)
        );
    }
}