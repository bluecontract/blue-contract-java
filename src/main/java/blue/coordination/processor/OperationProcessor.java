package blue.coordination.processor;

import blue.language.processor.ContractProcessor;
import blue.repo.coordination.Operation;

public final class OperationProcessor implements ContractProcessor<Operation> {
    @Override
    public Class<Operation> contractType() {
        return Operation.class;
    }
}
