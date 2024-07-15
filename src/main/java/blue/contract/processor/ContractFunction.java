package blue.contract.processor;

import blue.contract.utils.JSExecutor;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

public class ContractFunction implements ProxyExecutable {

    private final JSExecutor jsExecutor;

    public ContractFunction(JSExecutor jsExecutor) {
        this.jsExecutor = jsExecutor;
    }

    @Override
    public Object execute(Value... arguments) {
        if (arguments.length == 1 && arguments[0].isString()) {
            String path = arguments[0].asString();
            return jsExecutor.contract(path);
        }
        throw new IllegalArgumentException("Invalid arguments for contract function");
    }
}