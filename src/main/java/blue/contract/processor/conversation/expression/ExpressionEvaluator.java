package blue.contract.processor.conversation.expression;

import blue.contract.processor.conversation.workflow.StepExecutionContext;
import blue.language.model.Node;

public interface ExpressionEvaluator {
    Node evaluate(Node value, StepExecutionContext context);
}
