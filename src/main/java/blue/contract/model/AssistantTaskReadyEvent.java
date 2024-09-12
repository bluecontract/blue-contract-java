package blue.contract.model;

import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class AssistantTaskReadyEvent {
    private AssistantTask task;

    public AssistantTask getTask() {
        return task;
    }

    public AssistantTaskReadyEvent assistantTask(AssistantTask task) {
        this.task = task;
        return this;
    }
}