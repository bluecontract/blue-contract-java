package blue.contract.processor.conversation.workflow;

import blue.language.model.Node;
import blue.language.processor.ProcessorExecutionContext;
import blue.repo.v1_3_0.conversation.SequentialWorkflow;
import blue.repo.v1_3_0.conversation.SequentialWorkflowStep;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StepExecutionContext {
    private final ProcessorExecutionContext processorContext;
    private final SequentialWorkflow workflow;
    private final SequentialWorkflowStep step;
    private final Node stepNode;
    private final Node currentContractNode;
    private final int stepIndex;
    private final Map<String, Object> stepResults;
    private final Node event;

    public StepExecutionContext(ProcessorExecutionContext processorContext,
                                SequentialWorkflow workflow,
                                SequentialWorkflowStep step,
                                Node stepNode,
                                Node currentContractNode,
                                int stepIndex,
                                Map<String, Object> stepResults) {
        if (processorContext == null) {
            throw new IllegalArgumentException("processorContext must not be null");
        }
        if (workflow == null) {
            throw new IllegalArgumentException("workflow must not be null");
        }
        this.processorContext = processorContext;
        this.workflow = workflow;
        this.step = step;
        this.stepNode = stepNode != null ? stepNode.clone() : null;
        this.currentContractNode = currentContractNode != null ? currentContractNode.clone() : null;
        this.stepIndex = stepIndex;
        this.stepResults = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(
                stepResults != null ? stepResults : Collections.<String, Object>emptyMap()));
        Node currentEvent = processorContext.event();
        this.event = currentEvent != null ? currentEvent.clone() : null;
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

    public Node stepNode() {
        return stepNode != null ? stepNode.clone() : null;
    }

    public Node currentContractNode() {
        return currentContractNode != null ? currentContractNode.clone() : null;
    }

    public int stepIndex() {
        return stepIndex;
    }

    public Map<String, Object> stepResults() {
        return stepResults;
    }

    public Node event() {
        return event != null ? event.clone() : null;
    }
}
