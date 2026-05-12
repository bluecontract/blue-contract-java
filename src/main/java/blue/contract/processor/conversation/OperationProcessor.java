package blue.contract.processor.conversation;

import blue.language.processor.ContractProcessor;
import blue.repo.v1_2_0.conversation.Operation;

public final class OperationProcessor implements ContractProcessor<Operation> {
    @Override
    public Class<Operation> contractType() {
        return Operation.class;
    }
}
