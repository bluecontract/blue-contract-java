package blue.contract.model;

import blue.contract.model.step.WorkflowStep;
import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import java.util.List;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class Workflow {
    public Node trigger;
    public List<? extends WorkflowStep> steps;
}
