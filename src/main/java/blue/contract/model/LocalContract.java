package blue.contract.model;

import blue.language.model.BlueId;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class LocalContract {
    private Integer id;

    public Integer getId() {
        return id;
    }

    public LocalContract id(Integer id) {
        this.id = id;
        return this;
    }
}
