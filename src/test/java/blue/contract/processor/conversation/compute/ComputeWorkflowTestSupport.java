package blue.contract.processor.conversation.compute;

import blue.contract.processor.BlueDocumentProcessorOptions;
import blue.contract.processor.BlueDocumentProcessors;
import blue.contract.processor.conversation.ConversationTestResources;
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
        Blue blue = ConversationTestResources.configuredBlue(repository);
        BlueDocumentProcessors.registerWith(blue, options);
        TestTimelineProvider.registerWith(blue);
        return new ComputeWorkflowTestSupport(repository, blue);
    }

    Node yaml(String source) {
        Node node = blue.parseSourceYaml(source);
        node.blue(repository.typeAliasBlue());
        return blue.preprocess(node);
    }

    Node yamlResource(String resourcePath) {
        return ConversationTestResources.yamlResource(blue, repository, resourcePath);
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
        return operationRequest("owner", timestamp++, operation, request);
    }

    Node operationRequest(String timelineId, int timestamp, String operation, Node request) {
        return ConversationTestResources.operationRequestEvent(blue,
                repository,
                timelineId,
                timestamp,
                operation,
                request);
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
                ConversationTestResources.simpleTimelineChannelYaml("ownerChannel", "owner", 2),
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
                ConversationTestResources.simpleTimelineChannelYaml("ownerChannel", "owner", 2),
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
