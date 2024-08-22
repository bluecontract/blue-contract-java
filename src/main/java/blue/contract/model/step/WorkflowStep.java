package blue.contract.model.step;

import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public abstract class WorkflowStep {
    public String name;
    public String description;

}
