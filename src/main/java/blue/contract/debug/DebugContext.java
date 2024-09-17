package blue.contract.debug;

import blue.contract.WorkflowProcessor;
import blue.contract.WorkflowProcessor.ProcessingMode;
import blue.language.model.Node;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DebugContext {
    private DebugInfo currentDebugInfo;
    private DebugContractProcessingInfo currentContractInfo;
    private DebugWorkflowProcessingInfo currentWorkflowInfo;
    private boolean debug;

    public DebugContext(boolean debug) {
        this.debug = debug;
        if (debug) {
            currentDebugInfo = new DebugInfo();
        }
    }

    public void startContractProcessing(Node incomingEvent, Node contract) {
        if (debug) {
            currentContractInfo = new DebugContractProcessingInfo();
            currentContractInfo.incomingEvent(incomingEvent.clone());
            currentContractInfo.contract(contract.clone());
            currentDebugInfo.getContractResults().add(currentContractInfo);
            currentWorkflowInfo = null;
        }
    }

    public void startWorkflowProcessing(String workflowName) {
        startWorkflowProcessing(workflowName, null);
    }

    public void startWorkflowProcessing(String workflowName, Map<String, Object> initialStepResults) {
        if (debug) {
            currentWorkflowInfo = new DebugWorkflowProcessingInfo();
            currentWorkflowInfo.workflowName(workflowName == null ? "<Unknown>" : workflowName);
            if (initialStepResults != null) {
                currentWorkflowInfo.initialStepResultsMap(initialStepResults.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue() instanceof Node ? ((Node) e.getValue()).clone() : "<Not a Node>"
                        )));
            }
            currentContractInfo.getWorkflowResults().add(currentWorkflowInfo);
        }
    }

    public void skipWorkflowStep(ProcessingMode processingMode, String stepName) {
        if (debug) {
            DebugWorkflowStepProcessingInfo stepInfo = new DebugWorkflowStepProcessingInfo();
            stepInfo.mode(processingMode);
            stepInfo.stepName(stepName);
            stepInfo.skipping(true);
            currentWorkflowInfo.getStepResults().add(stepInfo);
        }
    }

    public void addWorkflowStepResult(Optional<String> stepName, Map<String, Object> results) {
        if (debug) {
            addWorkflowStepResult(stepName.orElse("<UnnamedStep>"), results);
        }
    }

    public void addWorkflowStepResult(String stepName,  Map<String, Object> results) {
        if (debug) {
            DebugWorkflowStepProcessingInfo stepInfo = new DebugWorkflowStepProcessingInfo();
            stepInfo.stepName(stepName);
            stepInfo.results(results);
            currentWorkflowInfo.getStepResults().add(stepInfo);
        }
    }

    public boolean isDebug() {
        return debug;
    }

    public DebugInfo getDebugInfo() {
        return currentDebugInfo;
    }
}