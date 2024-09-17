package blue.contract.debug;

import blue.contract.model.ContractProcessingContext;
import blue.contract.model.ExternalContract;
import blue.contract.model.LocalContract;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.event.ContractProcessingEvent;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.limits.CompositeLimits;
import blue.language.utils.limits.PathLimits;

import java.util.HashMap;
import java.util.Map;

public class DebugUtils {

    public static Map<String, Object> nodeMatchingDetails(Blue blue, Node event, Node expectedEvent) {
        PathLimits pathLimits = PathLimits.fromNode(expectedEvent);
        CompositeLimits compositeLimits = new CompositeLimits(blue.getGlobalLimits(), pathLimits);

        Node extendedNode = event.clone();
        blue.extend(extendedNode, compositeLimits);
        Node resolvedNode = blue.resolve(extendedNode, compositeLimits);
        Node resolvedType = blue.resolve(expectedEvent, compositeLimits);

        return Map.of("resolvedEvent", blue.nodeToSimpleYaml(resolvedNode), "resolvedType", blue.nodeToSimpleYaml(resolvedType));

    }

    public static Map<String, Object> contractProcessingEventMatchingDetails(ContractProcessingEvent contractProcessingEvent,
                                                                             Object contract, WorkflowProcessingContext context) {
        ContractProcessingContext contractProcessingContext = context.getContractProcessingContext();
        Integer currentContractInstanceId = contractProcessingContext.getContractInstanceId();
        String currentInitiateContractEntryBlueId = contractProcessingContext.getInitiateContractEntryBlueId();

        if (contract instanceof LocalContract localContract) {
            currentContractInstanceId = localContract.getId();
        } else if (contract instanceof ExternalContract externalContract) {
            currentContractInstanceId = externalContract.getLocalContractInstanceId();
            currentInitiateContractEntryBlueId = externalContract.getInitiateContractEntry();
        }

        Map<String, Object> map = new HashMap<>();
        map.put("currentContractInstanceId", currentContractInstanceId);
        map.put("currentInitiateContractEntryBlueId", currentInitiateContractEntryBlueId);
        map.put("contractProcessingEventContractInstanceId", contractProcessingEvent.getContractInstanceId());
        map.put("contractProcessingEventInitiateContractEntryBlueId", contractProcessingEvent.getInitiateContractEntry().get("/blueId"));
        return map;

    }

}
