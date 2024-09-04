package blue.contract.simulator;

import blue.contract.simulator.model.SimulatorTimelineEntry;
import blue.language.Blue;

import java.util.concurrent.CountDownLatch;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Blue blue = new Blue();
        SimulatorMT simulator = new SimulatorMT(blue);

        // Create timelines
        String aliceTimeline = simulator.createTimeline("Alice");
        String bobTimeline = simulator.createTimeline("Bob");

        // Create a latch to wait for all messages to be processed
        CountDownLatch latch = new CountDownLatch(4);

        // Subscribe to Alice's timeline
        simulator.subscribe(
                entry -> entry.getTimeline().equals(aliceTimeline),
                entry -> {
                    System.out.println("Received message on Alice's timeline: " + entry.getMessage());
                    latch.countDown();
                }
        );

        // Subscribe to Bob's timeline
        simulator.subscribe(
                entry -> entry.getTimeline().equals(bobTimeline),
                entry -> {
                    System.out.println("Received message on Bob's timeline: " + entry.getMessage());
                    latch.countDown();
                }
        );

        // Append entries to timelines
        simulator.appendEntry(aliceTimeline, "Hello from Alice!");
        simulator.appendEntry(bobTimeline, "Hi there, I'm Bob!");
        simulator.appendEntry(aliceTimeline, "How are you doing, Bob?");
        simulator.appendEntry(bobTimeline, "I'm doing great, thanks for asking!");

        // Wait for all messages to be processed
        latch.await();

        // Shutdown the simulator
        simulator.shutdown();
    }
}