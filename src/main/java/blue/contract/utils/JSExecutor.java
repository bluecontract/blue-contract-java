package blue.contract.utils;

import blue.language.Blue;
import blue.language.NodeProvider;
import blue.language.model.Node;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JSExecutor implements AutoCloseable {
    private final Context context;
    private final Blue blue;

    public JSExecutor(Blue blue) {
        this.blue = blue;
        this.context = Context.newBuilder("js")
                .allowAllAccess(true)
                .option("js.esm-eval-returns-exports", "true")
                .option("js.ecmascript-version", "2022")
                .option("engine.WarnInterpreterOnly", "false")
                .build();

        context.getBindings("js").putMember("importBlueESModule", new ProxyExecutable() {
            @Override
            public Object execute(Value... arguments) {
                if (arguments.length < 1 || !arguments[0].isString()) {
                    throw new IllegalArgumentException("importBlueESModule expects a string argument");
                }
                String moduleName = arguments[0].asString();
                try {
                    return loadModule(moduleName);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load module: " + moduleName, e);
                }
            }
        });
    }

    private Value loadModule(String uri) throws Exception {
        if (!uri.startsWith("blue:") || uri.length() <= 5) {
            throw new IllegalArgumentException("Invalid URI format. Expected 'blue:<blueId>'");
        }

        String blueId = uri.substring(5); // Remove "blue:" prefix

        NodeProvider nodeProvider = blue.getNodeProvider();
        Node node = nodeProvider.fetchFirstByBlueId(blueId);

        if (node == null) {
            throw new IllegalArgumentException("No module found for blueId: " + blueId);
        }

        String moduleContent = (String) node.getValue();

        Source source = Source.newBuilder("js", moduleContent, "module-" + blueId)
                .mimeType("application/javascript+module")
                .build();
        Value module = context.eval(source);

        // Use the blueId as the global name (without the "blue:" prefix)
        context.getBindings("js").putMember(blueId, module);

        return module;
    }


    public Object executeScript(String code, Map<String, Object> bindings) throws JSException {
        String setup =
                "class RejectAndAwaitNextEventException extends Error {\n" +
                "  constructor(message) {\n" +
                "    super(message);\n" +
                "    this.name = 'RejectAndAwaitNextEventException';\n" +
                "  }\n" +
                "}\n" +
                "class TerminateContractWithErrorException extends Error {\n" +
                "  constructor(message) {\n" +
                "    super(message);\n" +
                "    this.name = 'TerminateContractWithErrorException';\n" +
                "  }\n" +
                "}\n" +
                "function completeContract(message) {\n" +
                "  return { __processControl: true, action: 'completeContract', message: message };\n" +
                "}\n";

        String wrappedCode = setup +
                             "(() => {\n" +
                             "  try {\n" +
                             "    const result = (() => {\n" +
                             code + "\n" +
                             "    })();\n" +
                             "    if (result && result.__processControl) return result;\n" +
                             "    return { __normalReturn: true, value: result };\n" +
                             "  } catch (e) {\n" +
                             "    if (e instanceof RejectAndAwaitNextEventException || e instanceof TerminateContractWithErrorException) {\n" +
                             "      throw e;\n" +
                             "    }\n" +
                             "    throw new Error('Unexpected error: ' + e.message);\n" +
                             "  }\n" +
                             "})()";

        Source source = Source.newBuilder("js", wrappedCode, "jsCode").buildLiteral();

        bindings.forEach((key, value) -> context.getBindings("js").putMember(key, value));

        try {
            Value result = context.eval(source);
            return handleResult(result);
        } catch (PolyglotException e) {
            return handleException(e);
        }
    }

    public Object executeExpression(String code, Map<String, Object> bindings) throws JSException {
        Source source = Source.newBuilder("js", code, "jsCode").buildLiteral();

        bindings.forEach((key, value) -> context.getBindings("js").putMember(key, value));

        try {
            Value result = context.eval(source);
            return handleResult(result);
        } catch (PolyglotException e) {
            return handleException(e);
        }
    }

    private Object handleResult(Value result) throws JSException {
        Object javaResult = valueToObject(result);
        if (javaResult instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) javaResult;
            if (Boolean.TRUE.equals(resultMap.get("__processControl"))) {
                String action = (String) resultMap.get("action");
                if ("completeContract".equals(action)) {
                    return new ContractCompleteResult((String) resultMap.get("message"));
                }
            } else if (Boolean.TRUE.equals(resultMap.get("__normalReturn"))) {
                return resultMap.get("value");
            }
        }
        return blue.objectToNode(javaResult);
    }

    private Object handleException(PolyglotException e) throws JSException {
        if (e.isGuestException()) {
            Value exceptionObject = e.getGuestObject();
            if (exceptionObject != null) {
                String exceptionName = exceptionObject.getMember("name").asString();
                String message = exceptionObject.getMember("message").asString();
                switch (exceptionName) {
                    case "RejectAndAwaitNextEventException":
                        throw new RejectAndAwaitNextEventException(message);
                    case "TerminateContractWithErrorException":
                        throw new TerminateContractWithErrorException(message);
                }
            }
        }
        throw new JSCriticalException("JavaScript execution error", e);
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
                return !sb.isEmpty() ? sb.toString() : "No specific JavaScript stack trace available";
            }
            return "No JavaScript stack trace available";
        }

        private String formatStackFrame(PolyglotException.StackFrame frame) {
            String functionName = frame.getRootName();
            String fileName = frame.getSourceLocation() != null ? frame.getSourceLocation().getSource().getName() : "unknown";
            int lineNumber = frame.getSourceLocation() != null ? frame.getSourceLocation().getStartLine() : -1;
            int columnNumber = frame.getSourceLocation() != null ? frame.getSourceLocation().getStartColumn() : -1;

            return String.format("    at %s (%s:%d:%d)",
                    functionName.isEmpty() ? "<anonymous>" : functionName,
                    fileName,
                    lineNumber,
                    columnNumber);
        }

        public String getJsStackTrace() {
            return jsStackTrace;
        }

        @Override
        public String getMessage() {
            return super.getMessage() + (jsStackTrace != null ? "\nJavaScript stack trace:\n" + jsStackTrace : "");
        }
    }

    public static class RejectAndAwaitNextEventException extends JSException {
        public RejectAndAwaitNextEventException(String message) {
            super(message);
        }
    }

    public static class TerminateContractWithErrorException extends JSException {
        public TerminateContractWithErrorException(String message) {
            super(message);
        }
    }

    public static class ContractCompleteResult {
        private final String message;

        public ContractCompleteResult(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}