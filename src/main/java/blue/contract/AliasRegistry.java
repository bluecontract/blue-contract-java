package blue.contract;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AliasRegistry {

    private static final Map<String, String> INTERNAL_MAP = new HashMap<>();

    static {
        INTERNAL_MAP.put("Blue Contracts v0.4", "8jXV2NekFDvDELg1gyEPKtF3hGx9w9LvPg8gXQ2jTsbt");
    }

    public static final Map<String, String> MAP = Collections.unmodifiableMap(INTERNAL_MAP);

    private AliasRegistry() {
    }

}