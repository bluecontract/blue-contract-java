package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.contract.utils.ExpressionEvaluator;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.limits.Limits;
import blue.language.utils.limits.PathLimits;

import java.util.Optional;

public class ExpectEventStepProcessor extends AbstractStepProcessor {

    public ExpectEventStepProcessor(Node step, ExpressionEvaluator expressionEvaluator) {
        super(step, expressionEvaluator);
    }

    @Override
    public Optional<WorkflowInstance> handleEvent(Node event, WorkflowProcessingContext context) {
        Node expectedEventNode = step.getProperties().get("event").clone();
        expectedEventNode = evaluateExpressionsRecursively(expectedEventNode, context);

        if (context.getContractProcessingContext().getBlue().nodeMatchesType(event, expectedEventNode))
            return finalizeNextStepByOrder(event, context);
        else
            return Optional.empty();
    }

    @Override
    public Optional<WorkflowInstance> finalizeEvent(Node event, WorkflowProcessingContext workflowProcessingContext) {
        WorkflowInstance workflowInstance = workflowProcessingContext.getWorkflowInstance();
        workflowInstance.currentStepName(step.getName());
        
        return Optional.of(workflowInstance);
    }
}
