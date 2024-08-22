package blue.contract.model.step;

import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04, defaultValueRepositoryKey = "JavaScript Code Step")
public class JavaScriptCodeStep extends WorkflowStep {
    private String code;

    public String getCode() {
        return code;
    }

    public JavaScriptCodeStep code(String code) {
        this.code = code;
        return this;
    }
}
