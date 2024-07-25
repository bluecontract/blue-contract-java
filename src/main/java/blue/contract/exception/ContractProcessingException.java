package blue.contract.exception;

public class ContractProcessingException extends RuntimeException {
    public ContractProcessingException(String message) {
        super(message);
    }

    public ContractProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}