package blue.contract.processor.conversation.compute;

import blue.contract.processor.BlueDocumentProcessorOptions;
import blue.contract.processor.BlueDocumentProcessors;
import blue.contract.processor.conversation.TestTimelineProvider;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.repo.BlueRepository;

final class ComputeWorkflowTestSupport {
    private int timestamp = 1;

    final BlueRepository repository;
    final Blue blue;

    private ComputeWorkflowTestSupport(BlueRepository repository, Blue blue) {
        this.repository = repository;
        this.blue = blue;
    }

    static ComputeWorkflowTestSupport create() {
        return create(null);
    }

    static ComputeWorkflowTestSupport create(BlueDocumentProcessorOptions options) {
        BlueRepository repository = BlueRepository.v1_3_0();
        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        BlueDocumentProcessors.registerWith(blue, options);
        TestTimelineProvider.registerWith(blue);
        return new ComputeWorkflowTestSupport(repository, blue);
    }

    Node yaml(String source) {
        Node node = blue.yamlToNode(source);
        node.blue(repository.typeAliasBlue());
        return blue.preprocess(node);
    }

    DocumentProcessingResult initialize(Node document) {
        return blue.initializeDocument(blue.preprocess(document));
    }

    DocumentProcessingResult process(Node snapshot, Node event) {
        return blue.processDocument(snapshot, event);
    }

    DocumentProcessingResult processRun(Node snapshot) {
        return processRun(snapshot, new Node().value("request"));
    }

    DocumentProcessingResult processRun(Node snapshot, Node request) {
        return process(snapshot, operationRequest("run", request));
    }

    Node operationRequest(String operation, Node request) {
        Node message = new Node()
                .type("Conversation/Operation Request")
                .properties("operation", new Node().value(operation))
                .properties("request", request);
        return TestTimelineProvider.timelineEntry(blue, repository, "owner", timestamp++, message);
    }

    String operationWorkflowDocument(String body) {
        return operationWorkflowDocumentWithContracts("", body);
    }

    String operationWorkflowDocumentWithStatus(String rootFields, String body) {
        return String.join("\n",
                "name: Compute Workflow Test",
                "status: idle",
                rootFields,
                "contracts:",
                "  ownerChannel:",
                "    type:",
                "      blueId: " + TestTimelineProvider.SIMPLE_TIMELINE_CHANNEL_BLUE_ID,
                "    timelineId: owner",
                "  run:",
                "    type: Conversation/Operation",
                "    channel: ownerChannel",
                "  runImpl:",
                "    type: Conversation/Sequential Workflow Operation",
                "    operation: run",
                body);
    }

    String operationWorkflowDocumentWithContracts(String extraContracts, String body) {
        return String.join("\n",
                "name: Compute Workflow Test",
                "status: idle",
                "contracts:",
                "  ownerChannel:",
                "    type:",
                "      blueId: " + TestTimelineProvider.SIMPLE_TIMELINE_CHANNEL_BLUE_ID,
                "    timelineId: owner",
                "  run:",
                "    type: Conversation/Operation",
                "    channel: ownerChannel",
                extraContracts,
                "  runImpl:",
                "    type: Conversation/Sequential Workflow Operation",
                "    operation: run",
                body);
    }

    Node initializedOperationWorkflow(String body) {
        return initialize(yaml(operationWorkflowDocument(body))).document();
    }
}
