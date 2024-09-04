package blue.contract.model;

import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class AssistantTask<Req, Resp> {
    private Req request;
    private Resp response;

    public Req getRequest() {
        return request;
    }

    public AssistantTask<Req, Resp> request(Req request) {
        this.request = request;
        return this;
    }

    public Resp getResponse() {
        return response;
    }

    public AssistantTask<Req, Resp> response(Resp response) {
        this.response = response;
        return this;
    }
}
