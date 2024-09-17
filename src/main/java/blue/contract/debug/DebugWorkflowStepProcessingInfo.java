package blue.contract.debug;

import blue.contract.WorkflowProcessor;
import blue.contract.WorkflowProcessor.ProcessingMode;

import java.util.HashMap;
import java.util.Map;

public class DebugWorkflowStepProcessingInfo {
    private String stepName;
    private Boolean skipping;
    private ProcessingMode mode;
    private Map<String, Object> results = new HashMap<>();

    public String getStepName() {
        return stepName;
    }

    public DebugWorkflowStepProcessingInfo stepName(String stepName) {
        this.stepName = stepName;
        return this;
    }

    public Boolean isSkipping() {
        return skipping;
    }

    public DebugWorkflowStepProcessingInfo skipping(Boolean skipping) {
        this.skipping = skipping;
        return this;
    }

    public Map<String, Object> getResults() {
        return results;
    }

    public DebugWorkflowStepProcessingInfo results(Map<String, Object> results) {
        this.results = results;
        return this;
    }

    public ProcessingMode getMode() {
        return mode;
    }

    public DebugWorkflowStepProcessingInfo mode(ProcessingMode mode) {
        this.mode = mode;
        return this;
    }
}
