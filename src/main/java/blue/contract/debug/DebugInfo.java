package blue.contract.debug;

import java.util.ArrayList;
import java.util.List;

public class DebugInfo {
    private List<DebugContractProcessingInfo> contractResults = new ArrayList<>();

    public List<DebugContractProcessingInfo> getContractResults() {
        return contractResults;
    }

    public DebugInfo contractResults(List<DebugContractProcessingInfo> contractResults) {
        this.contractResults = contractResults;
        return this;
    }
}
