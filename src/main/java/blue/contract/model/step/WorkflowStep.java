package blue.contract.model.step;

import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public abstract class WorkflowStep {
    protected String name;
    protected String description;

    public String getName() {
        return name;
    }

    public WorkflowStep name(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public WorkflowStep description(String description) {
        this.description = description;
        return this;
    }
}
