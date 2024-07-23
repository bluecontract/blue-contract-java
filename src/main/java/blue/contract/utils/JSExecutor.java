package blue.contract.utils;

import blue.contract.processor.CustomFileSystem;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JSExecutor {
    private final FileSystem customFS;

    public JSExecutor() {
        this.customFS = new CustomFileSystem(FileSystem.newDefaultFileSystem());
    }

    public Object executeScript(String code, Map<String, Object> bindings) throws IOException {
        Source source = Source.newBuilder("js", code, "jsCode")
                .mimeType("application/javascript+module")
                .build();
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true)
                .allowIO(IOAccess.newBuilder()
                        .fileSystem(customFS)
                        .build())
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