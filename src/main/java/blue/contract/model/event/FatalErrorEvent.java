package blue.contract.model.event;

import blue.language.model.BlueId;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class FatalErrorEvent {

    private String errorMessage;
    private String stackTrace;

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public FatalErrorEvent errorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public FatalErrorEvent stackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
        return this;
    }
}