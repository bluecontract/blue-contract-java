package blue.contract.processor.conversation.workflow;

import blue.language.model.Node;
import blue.language.processor.ProcessorExecutionContext;
import blue.language.processor.WorkingDocument;
import blue.language.processor.model.JsonPatch;
import blue.language.snapshot.FrozenNode;
import blue.repo.conversation.SequentialWorkflow;
import blue.repo.conversation.SequentialWorkflowStep;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StepExecutionContext {
    private final ProcessorExecutionContext processorContext;
    private final SequentialWorkflow workflow;
    private final SequentialWorkflowStep step;
    private Node stepNodeRef;
    private Node currentContractNodeRef;
    private final FrozenNode stepFrozenNode;
    private final FrozenNode currentContractFrozenNode;
    private final int stepIndex;
    private final Map<String, Object> stepResults;
    private final Node eventRef;
    private WorkingDocument workingDocument;

    public StepExecutionContext(ProcessorExecutionContext processorContext,
                                SequentialWorkflow workflow,
                                SequentialWorkflowStep step,
                                Node stepNode,
                                Node currentContractNode,
                                int stepIndex,
                                Map<String, Object> stepResults) {
        this(processorContext,
                workflow,
                step,
                stepNode,
                currentContractNode,
                null,
                null,
                stepIndex,
                stepResults,
                null);
    }

    public StepExecutionContext(ProcessorExecutionContext processorContext,
                                SequentialWorkflow workflow,
                                SequentialWorkflowStep step,
                                FrozenNode stepFrozenNode,
                                FrozenNode currentContractFrozenNode,
                                int stepIndex,
                                Map<String, Object> stepResults) {
        this(processorContext,
                workflow,
                step,
                null,
                null,
                stepFrozenNode,
                currentContractFrozenNode,
                stepIndex,
                stepResults,
                null);
    }

    StepExecutionContext(ProcessorExecutionContext processorContext,
                         SequentialWorkflow workflow,
                         SequentialWorkflowStep step,
                         FrozenNode stepFrozenNode,
                         FrozenNode currentContractFrozenNode,
                         int stepIndex,
                         Map<String, Object> stepResults,
                         WorkingDocument workingDocument) {
        this(processorContext,
                workflow,
                step,
                null,
                null,
                stepFrozenNode,
                currentContractFrozenNode,
                stepIndex,
                stepResults,
                workingDocument);
    }

    private StepExecutionContext(ProcessorExecutionContext processorContext,
                                 SequentialWorkflow workflow,
                                 SequentialWorkflowStep step,
                                 Node stepNode,
                                 Node currentContractNode,
                                 FrozenNode stepFrozenNode,
                                 FrozenNode currentContractFrozenNode,
                                 int stepIndex,
                                 Map<String, Object> stepResults,
                                 WorkingDocument workingDocument) {
        if (processorContext == null) {
            throw new IllegalArgumentException("processorContext must not be null");
        }
        if (workflow == null) {
            throw new IllegalArgumentException("workflow must not be null");
        }
        this.processorContext = processorContext;
        this.workflow = workflow;
        this.step = step;
        this.stepNodeRef = stepNode;
        this.currentContractNodeRef = currentContractNode;
        this.stepFrozenNode = stepFrozenNode;
        this.currentContractFrozenNode = currentContractFrozenNode;
        this.stepIndex = stepIndex;
        this.stepResults = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(
                stepResults != null ? stepResults : Collections.<String, Object>emptyMap()));
        this.eventRef = processorContext.event();
        this.workingDocument = workingDocument;
    }

    public ProcessorExecutionContext processorContext() {
        return processorContext;
    }

    public SequentialWorkflow workflow() {
        return workflow;
    }

    public SequentialWorkflowStep step() {
        return step;
    }

    public Node stepNodeRef() {
        if (stepNodeRef == null && stepFrozenNode != null) {
            stepNodeRef = stepFrozenNode.toNode();
        }
        return stepNodeRef;
    }

    public Node currentContractNodeRef() {
        if (currentContractNodeRef == null && currentContractFrozenNode != null) {
            currentContractNodeRef = currentContractFrozenNode.toNode();
        }
        return currentContractNodeRef;
    }

    public FrozenNode stepFrozenNode() {
        return stepFrozenNode;
    }

    public FrozenNode currentContractFrozenNode() {
        return currentContractFrozenNode;
    }

    public Node eventRef() {
        return eventRef;
    }

    /**
     * Compatibility getter. Existing JavaScript paths rely on clone semantics.
     */
    public Node stepNode() {
        Node ref = stepNodeRef();
        return ref != null ? ref.clone() : null;
    }

    public Node currentContractNode() {
        Node ref = currentContractNodeRef();
        return ref != null ? ref.clone() : null;
    }

    public int stepIndex() {
        return stepIndex;
    }

    public Map<String, Object> stepResults() {
        return stepResults;
    }

    public Node event() {
        return eventRef != null ? eventRef.clone() : null;
    }

    public Node documentView() {
        FrozenNode scoped = workingDocument().canonicalAt(processorContext.resolvePointer("/"));
        return scoped != null ? scoped.toNode() : null;
    }

    public WorkingDocument workingDocument() {
        if (workingDocument == null) {
            workingDocument = processorContext.newWorkingDocument();
        }
        return workingDocument;
    }

    public FrozenNode workingCanonicalAt(String absolutePointer) {
        if (absolutePointer == null || absolutePointer.isEmpty()) {
            return null;
        }
        return workingDocument().canonicalAt(absolutePointer);
    }

    public FrozenNode workingResolvedAt(String absolutePointer) {
        if (absolutePointer == null || absolutePointer.isEmpty()) {
            return null;
        }
        return workingDocument().resolvedAt(absolutePointer);
    }

    WorkingDocument.Preview advanceWorkingDocument(List<JsonPatch> patches) {
        if (patches == null || patches.isEmpty()) {
            return null;
        }
        try {
            return workingDocument().previewAndApplyPatches(patches);
        } catch (RuntimeException ex) {
            processorContext.throwFatal("Working document preview failed: " + ex.getMessage());
            return null;
        }
    }
}
