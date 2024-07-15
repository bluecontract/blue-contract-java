package blue.contract;

import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.language.model.Node;

import java.util.Optional;

public interface StepProcessor {
    Optional<WorkflowInstance> handleEvent(Node event, WorkflowProcessingContext workflowProcessingContext);
    Optional<WorkflowInstance> finalizeEvent(Node event, WorkflowProcessingContext workflowProcessingContext);
}