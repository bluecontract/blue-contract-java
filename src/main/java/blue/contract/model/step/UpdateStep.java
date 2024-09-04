package blue.contract.model.step;

import blue.language.model.TypeBlueId;

import java.util.List;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class UpdateStep extends WorkflowStep {
    private List<JsonPatchEntry> changeset;

    public List<JsonPatchEntry> getChangeset() {
        return changeset;
    }

    public UpdateStep changeset(List<JsonPatchEntry> changeset) {
        this.changeset = changeset;
        return this;
    }
}
