package blue.contract.model;

import java.util.HashMap;
import java.util.Map;

public class ContractProcessorConfig {

    public static final String EVENT_TARGET_TYPE_TRANSFORMER = "eventTargetTypeTransformer";

    private Map<String, Object> configMap = new HashMap<>();

    public void set(String key, Object value) {
        configMap.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) configMap.get(key);
    }

    public boolean has(String key) {
        return configMap.containsKey(key);
    }
}