package blue.contract.model;

import blue.language.model.BlueId;

@BlueId("GGo3aanJBb5DdzXovf7ibiBs3bZkXzHeizTCgYFhLWpQ")
public class LocalContract {
    private Integer id;

    public Integer getId() {
        return id;
    }

    public LocalContract id(int id) {
        this.id = id;
        return this;
    }
}
