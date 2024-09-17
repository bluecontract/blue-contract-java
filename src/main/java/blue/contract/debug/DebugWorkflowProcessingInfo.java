package blue.contract.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DebugWorkflowProcessingInfo {
    private String workflowName;
    private Map<String, Object> initialStepResultsMap;
    private List<DebugWorkflowStepProcessingInfo> stepResults = new ArrayList<>();

    public String getWorkflowName() {
        return workflowName;
    }

    public DebugWorkflowProcessingInfo workflowName(String workflowName) {
        this.workflowName = workflowName;
        return this;
    }

    public List<DebugWorkflowStepProcessingInfo> getStepResults() {
        return stepResults;
    }

    public DebugWorkflowProcessingInfo stepResults(List<DebugWorkflowStepProcessingInfo> stepResults) {
        this.stepResults = stepResults;
        return this;
    }

    public Map<String, Object> getInitialStepResultsMap() {
        return initialStepResultsMap;
    }

    public DebugWorkflowProcessingInfo initialStepResultsMap(Map<String, Object> initialStepResultsMap) {
        this.initialStepResultsMap = initialStepResultsMap;
        return this;
    }
}
