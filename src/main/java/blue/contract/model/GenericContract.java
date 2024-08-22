package blue.contract.model;

import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import java.util.Map;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class GenericContract extends Contract {
    private Map<String, Node> properties;

    public Map<String, Node> getProperties() {
        return properties;
    }

    public GenericContract properties(Map<String, Node> properties) {
        this.properties = properties;
        return this;
    }
}
