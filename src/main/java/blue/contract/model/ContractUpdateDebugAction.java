package blue.contract.model;


import blue.contract.debug.DebugInfo;

public class ContractUpdateDebugAction {
    private DebugInfo debug;

    public DebugInfo getDebug() {
        return debug;
    }

    public ContractUpdateDebugAction debug(DebugInfo debug) {
        this.debug = debug;
        return this;
    }
}
