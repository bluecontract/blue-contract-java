package blue.contract.model;

import blue.language.model.BlueId;

@BlueId("6gBMYGeWw1Cutbsrzj3c98RH4VrSJNvPsgZ4F4A19i3f")
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
