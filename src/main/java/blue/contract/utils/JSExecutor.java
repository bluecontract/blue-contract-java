package blue.contract.utils;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JSExecutor {
    public Object executeScript(String code, Map<String, Object> bindings) throws IOException {
        Source source = Source.newBuilder("js", code, "jsCode").build();
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {

            bindings.forEach(context.getBindings("js")::putMember);

            Value result = context.eval(source);
            return valueToObject(result);
        }
    }

    private Object valueToObject(Value value) {
        if (value.isHostObject()) {
            return value.asHostObject();
        } else if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < value.getArraySize(); i++) {
                list.add(valueToObject(value.getArrayElement(i)));
            }
            return list;
        } else if (value.hasMembers()) {
            Map<String, Object> map = new HashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, valueToObject(value.getMember(key)));
            }
            return map;
        } else if (value.isString()) {
            return value.asString();
        } else if (value.isNumber()) {
            return value.asDouble();
        } else if (value.isBoolean()) {
            return value.asBoolean();
        } else if (value.isNull()) {
            return null;
        } else {
            throw new IllegalArgumentException("Unsupported value type: " + value);
        }
    }
}