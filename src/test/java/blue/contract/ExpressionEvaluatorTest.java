package blue.contract;

import blue.contract.model.WorkflowProcessingContext;
import blue.contract.utils.ExpressionEvaluator;
import blue.contract.utils.JSExecutor;
import blue.language.Blue;
import blue.contract.model.ContractProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.language.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static blue.contract.utils.ExpressionEvaluator.ExpressionScope.CURRENT_CONTRACT;
import static blue.contract.utils.Utils.testBlue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class ExpressionEvaluatorTest {

    private Blue blue;
    private JSExecutor jsExecutor;
    private ExpressionEvaluator expressionEvaluator;

    @Mock
    private WorkflowProcessingContext workflowContext;
    @Mock
    private ContractProcessingContext contractContext;
    @Mock
    private WorkflowInstance workflowInstance;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        blue = testBlue();
        jsExecutor = new JSExecutor(blue);
        expressionEvaluator = new ExpressionEvaluator(jsExecutor);

        when(workflowContext.getContractProcessingContext()).thenReturn(contractContext);
        when(workflowContext.getWorkflowInstance()).thenReturn(workflowInstance);
        when(contractContext.getBlue()).thenReturn(blue);

        Map<String, Object> stepResults = new HashMap<>();
        stepResults.put("userName", "Alice");
        when(workflowInstance.getStepResults()).thenReturn(stepResults);
    }

    @Test
    void testSimpleExpression() {
        String expression = "${steps.userName}";
        Object result = expressionEvaluator.evaluate(expression, workflowContext, CURRENT_CONTRACT, true);
        Node node = (Node) result;
        assertEquals("Alice", node.getValue());
    }

    @Test
    void testInterpolatedExpression() {
        String expression = "Hello, ${steps.userName.value}! Today is ${new Date().toDateString()}.";
        Object result = expressionEvaluator.evaluate(expression, workflowContext, CURRENT_CONTRACT, false);
        Node node = (Node) result;
        assertTrue(node.getValue().toString().startsWith("Hello, Alice! Today is"));
        assertTrue(node.getValue().toString().endsWith("."));
    }

    @Test
    void testPlainString() {
        String expression = "This is a plain string without any expressions.";
        Object result = expressionEvaluator.evaluate(expression, workflowContext, CURRENT_CONTRACT, false);
        assertEquals(expression, result);
    }

    @Test
    void testMultipleExpressionsAndJavaScriptLogic() {
        String expression = "${(() => { " +
                            "const name = steps.userName.value.toUpperCase(); " +
                            "const greeting = name.length > 5 ? 'Hello' : 'Hi'; " +
                            "return `${greeting}, ${name}! Your name has ${name.length} characters.`; " +
                            "})()}";
        Object result = expressionEvaluator.evaluate(expression, workflowContext, CURRENT_CONTRACT, false);
        Node node = (Node) result;
        assertEquals("Hi, ALICE! Your name has 5 characters.", node.getValue().toString());
    }
}