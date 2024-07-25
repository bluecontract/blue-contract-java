package blue.contract.utils;

import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class JSExecutor implements AutoCloseable {
    private final Context context;

    public JSExecutor() {
        this(className -> true);
    }

    public JSExecutor(Predicate<String> classFilter) {
        this.context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(classFilter)
                .option("js.ecmascript-version", "2022")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }

    public Object executeScript(String code, Map<String, Object> bindings) throws JSException {
        Source source = Source.newBuilder("js", code, "jsCode").buildLiteral();

        bindings.forEach((key, value) -> context.getBindings("js").putMember(key, value));

        try {
            Value result = context.eval(source);
            Object javaResult = valueToObject(result);

            if (javaResult instanceof Map) {
                Map<String, Object> resultMap = (Map<String, Object>) javaResult;
                if (resultMap.containsKey("controlAction")) {
                    String controlAction = (String) resultMap.get("controlAction");
                    String message = (String) resultMap.get("message");
                    throw new ProcessControlException(controlAction, message);
                }
            }

            return javaResult;
        } catch (PolyglotException e) {
            throw new JSCriticalException("JavaScript execution error", e);
        }
    }

    private Object valueToObject(Value value) {
        if (value.isNull()) {
            return null;
        } else if (value.isHostObject()) {
            return value.asHostObject();
        } else if (value.isProxyObject()) {
            return value.asProxyObject();
        } else if (value.hasArrayElements()) {
            return valueToList(value);
        } else if (value.hasMembers()) {
            return valueToMap(value);
        } else if (value.isString()) {
            return value.asString();
        } else if (value.isNumber()) {
            return value.fitsInLong() ? value.asLong() : value.asDouble();
        } else if (value.isBoolean()) {
            return value.asBoolean();
        } else if (value.canExecute()) {
            return (ProxyExecutable) args -> valueToObject(value.execute((Object[]) args));
        } else {
            return value.toString();
        }
    }

    private List<Object> valueToList(Value value) {
        List<Object> list = new ArrayList<>((int) value.getArraySize());
        for (long i = 0; i < value.getArraySize(); i++) {
            list.add(valueToObject(value.getArrayElement(i)));
        }
        return list;
    }

    private Map<String, Object> valueToMap(Value value) {
        Map<String, Object> map = new HashMap<>();
        for (String key : value.getMemberKeys()) {
            map.put(key, valueToObject(value.getMember(key)));
        }
        return map;
    }

    @Override
    public void close() {
        if (context != null) {
            context.close();
        }
    }

    public ProxyExecutable createJavaFunction(JavaScriptCallback callback) {
        return arguments -> {
            try {
                return callback.call(arguments);
            } catch (Exception e) {
                throw new RuntimeException("Error in Java callback", e);
            }
        };
    }

    public interface JavaScriptCallback {
        Object call(Value... arguments) throws Exception;
    }

    public static class JSException extends Exception {
        public JSException(String message) {
            super(message);
        }

        public JSException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class JSCriticalException extends JSException {
        private final String jsStackTrace;

        public JSCriticalException(String message, PolyglotException cause) {
            super(message, cause);
            this.jsStackTrace = formatPolyglotStackTrace(cause);
        }

        private String formatPolyglotStackTrace(PolyglotException e) {
            if (e.isGuestException()) {
                StringBuilder sb = new StringBuilder();
                boolean isFirstFrame = true;
                for (PolyglotException.StackFrame frame : e.getPolyglotStackTrace()) {
                    if (frame.isGuestFrame()) {
                        if (!isFirstFrame) {
                            sb.append("\n");
                        }
                        sb.append(formatStackFrame(frame));
                        isFirstFrame = false;
                    }
                }
                return sb.length() > 0 ? sb.toString() : "No specific JavaScript stack trace available";
            }
            return "No JavaScript stack trace available";
        }

        private String formatStackFrame(PolyglotException.StackFrame frame) {
            String location = frame.getSourceLocation() != null ? frame.getSourceLocation().toString() : "unknown";
            return String.format("at %s (%s)", frame.getRootName(), location);
        }

        public String getJsStackTrace() {
            return jsStackTrace;
        }

        @Override
        public String getMessage() {
            return super.getMessage() + (jsStackTrace != null ? "\nJavaScript stack trace:\n" + jsStackTrace : "");
        }
    }

    public static class ProcessControlException extends JSException {
        private final String controlAction;
        private final String message;

        public ProcessControlException(String controlAction, String message) {
            super(String.format("Workflow control action: [%s] %s", controlAction, message));
            this.controlAction = controlAction;
            this.message = message;
        }

        public String getControlAction() { return controlAction; }
        public String getMessage() { return message; }
    }
}