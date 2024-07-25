package blue.contract.model.event;

import blue.language.model.BlueId;

@BlueId("9xhgr2ZGd976hQG4ag6gufDxK3Td3Zcz8STF8AxMrxwi")
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