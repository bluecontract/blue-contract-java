package blue.contract.processor.conversation.javascript;

import blue.language.utils.UncheckedObjectMapper;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class NodeQuickJsRuntime implements JavaScriptRuntime, AutoCloseable {
    private static final String QUICKJS_ROOT_PROPERTY = "blue.quickjs.root";
    private static final String BRIDGE_RESOURCE = "/blue/contract/processor/quickjs/evaluate.mjs";
    private static final long DEFAULT_TIMEOUT_MILLIS = 10000L;
    private static final ObjectMapper REQUEST_MAPPER = new ObjectMapper(new JsonFactory())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);

    private final Path blueQuickJsRoot;
    private final Path bridgeScript;
    private final long timeoutMillis;
    private BridgeProcess bridge;

    public NodeQuickJsRuntime() {
        this(defaultBlueQuickJsRoot(), resourcePath(BRIDGE_RESOURCE), DEFAULT_TIMEOUT_MILLIS);
    }

    public NodeQuickJsRuntime(Path blueQuickJsRoot) {
        this(blueQuickJsRoot, resourcePath(BRIDGE_RESOURCE), DEFAULT_TIMEOUT_MILLIS);
    }

    public NodeQuickJsRuntime(Path blueQuickJsRoot, Path bridgeScript, long timeoutMillis) {
        if (blueQuickJsRoot == null) {
            throw new IllegalArgumentException("blueQuickJsRoot must not be null");
        }
        if (bridgeScript == null) {
            throw new IllegalArgumentException("bridgeScript must not be null");
        }
        if (timeoutMillis <= 0L) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        this.blueQuickJsRoot = blueQuickJsRoot.toAbsolutePath().normalize();
        this.bridgeScript = bridgeScript.toAbsolutePath().normalize();
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public synchronized JavaScriptEvaluationResult evaluate(JavaScriptEvaluationRequest request) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("code", request.code());
        payload.put("mode", request.mode().name().toLowerCase(Locale.ROOT));
        payload.put("hostGasLimit", request.hostGasLimit());
        payload.put("wasmGasLimit", Long.toString(QuickJsGas.toWasmFuel(request.hostGasLimit())));
        payload.put("bindings", request.bindings());

        String input = writeRequest(payload);
        BridgeProcess active = bridge();
        try {
            active.writeRequest(input);
            String stdout = active.readResponse(timeoutMillis);
            if (stdout == null) {
                active.destroy();
                bridge = null;
                throw new JavaScriptExecutionException("QuickJS evaluation timed out after " + timeoutMillis + " ms");
            }
            return parseResult(stdout);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            active.destroy();
            bridge = null;
            throw new JavaScriptExecutionException("QuickJS evaluation interrupted", ex);
        } catch (IOException ex) {
            active.destroy();
            bridge = null;
            throw new JavaScriptExecutionException("QuickJS bridge failed: " + firstNonEmpty(active.stderr(), ex.getMessage()), ex);
        }
    }

    @Override
    public synchronized void close() {
        if (bridge != null) {
            bridge.destroy();
            bridge = null;
        }
    }

    private BridgeProcess bridge() {
        if (bridge != null && bridge.isAlive()) {
            return bridge;
        }
        if (bridge != null) {
            bridge.destroy();
        }
        assertReady();
        bridge = startProcess();
        return bridge;
    }

    private BridgeProcess startProcess() {
        ProcessBuilder builder = new ProcessBuilder("node",
                bridgeScript.toString(),
                blueQuickJsRoot.toString());
        builder.directory(new File(blueQuickJsRoot.toString()));
        try {
            return new BridgeProcess(builder.start());
        } catch (IOException ex) {
            throw new JavaScriptExecutionException("Failed to start node for QuickJS evaluation", ex);
        }
    }

    private String writeRequest(Map<String, Object> payload) {
        try {
            return REQUEST_MAPPER.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new JavaScriptExecutionException("Failed to serialize QuickJS request", ex);
        }
    }

    JavaScriptEvaluationResult parseResult(String stdout) {
        Map<String, Object> result = UncheckedObjectMapper.JSON_MAPPER.readValue(stdout,
                new TypeReference<Map<String, Object>>() {
                });
        Object ok = result.get("ok");
        if (!Boolean.TRUE.equals(ok)) {
            Object message = result.get("message");
            Object type = result.get("type");
            String prefix = type != null ? type + ": " : "";
            String errorMessage = prefix + (message != null ? message : "unknown error");
            if (!result.containsKey("wasmGasUsed")) {
                throw new JavaScriptExecutionException(errorMessage);
            }
            long errorWasmGasUsed = parseLong(result.get("wasmGasUsed"), "wasmGasUsed");
            throw new JavaScriptExecutionException(errorMessage,
                    errorWasmGasUsed,
                    QuickJsGas.toHostGasUsed(errorWasmGasUsed));
        }
        long wasmGasUsed = parseLong(result.get("wasmGasUsed"), "wasmGasUsed");
        long hostGasUsed = QuickJsGas.toHostGasUsed(wasmGasUsed);
        return new JavaScriptEvaluationResult(result.get("value"), wasmGasUsed, hostGasUsed);
    }

    private long parseLong(Object value, String field) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ex) {
                throw new JavaScriptExecutionException("QuickJS bridge returned invalid " + field + ": " + value, ex);
            }
        }
        throw new JavaScriptExecutionException("QuickJS bridge returned invalid " + field + ": " + value);
    }

    private void assertReady() {
        if (!Files.isDirectory(blueQuickJsRoot)) {
            throw new JavaScriptExecutionException("blue-quickjs root not found: " + blueQuickJsRoot
                    + " (set -Dblue.quickjs.root=/path/to/blue-quickjs)");
        }
        Path runtimeDist = blueQuickJsRoot.resolve("libs/quickjs-runtime/dist/index.js");
        if (!Files.isRegularFile(runtimeDist)) {
            throw new JavaScriptExecutionException("blue-quickjs runtime dist is missing at " + runtimeDist
                    + "; build it with: cd " + blueQuickJsRoot + " && pnpm nx build quickjs-runtime");
        }
        Path manifestDist = blueQuickJsRoot.resolve("libs/abi-manifest/dist/index.js");
        if (!Files.isRegularFile(manifestDist)) {
            throw new JavaScriptExecutionException("blue-quickjs ABI manifest dist is missing at " + manifestDist
                    + "; build it with: cd " + blueQuickJsRoot + " && pnpm nx build quickjs-runtime");
        }
        if (!Files.isRegularFile(bridgeScript)) {
            throw new JavaScriptExecutionException("QuickJS bridge script not found: " + bridgeScript);
        }
    }

    private static Path defaultBlueQuickJsRoot() {
        String configured = System.getProperty(QUICKJS_ROOT_PROPERTY);
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured);
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().getParent().resolve("blue-quickjs");
    }

    private static Path resourcePath(String resourceName) {
        URL resource = NodeQuickJsRuntime.class.getResource(resourceName);
        if (resource == null) {
            throw new JavaScriptExecutionException("Missing QuickJS bridge resource: " + resourceName);
        }
        if ("file".equals(resource.getProtocol())) {
            try {
                return Paths.get(resource.toURI());
            } catch (URISyntaxException ex) {
                throw new JavaScriptExecutionException("Invalid QuickJS bridge resource URI", ex);
            }
        }
        try {
            Path temp = Files.createTempFile("blue-quickjs-evaluate-", ".mjs");
            temp.toFile().deleteOnExit();
            try (InputStream input = NodeQuickJsRuntime.class.getResourceAsStream(resourceName);
                 OutputStream output = Files.newOutputStream(temp)) {
                if (input == null) {
                    throw new JavaScriptExecutionException("Missing QuickJS bridge resource: " + resourceName);
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
            }
            return temp;
        } catch (IOException ex) {
            throw new JavaScriptExecutionException("Failed to extract QuickJS bridge resource", ex);
        }
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return "no output";
    }

    private static final class BridgeProcess {
        private final Process process;
        private final BufferedWriter stdin;
        private final BlockingQueue<String> stdoutLines = new LinkedBlockingQueue<String>();
        private final StringBuilder stderr = new StringBuilder();

        private BridgeProcess(Process process) {
            this.process = process;
            this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            startReader("blue-quickjs-stdout", process.getInputStream(), stdoutLines, null);
            startReader("blue-quickjs-stderr", process.getErrorStream(), null, stderr);
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    destroy();
                }
            }, "blue-quickjs-shutdown"));
        }

        private boolean isAlive() {
            return process.isAlive();
        }

        private synchronized void writeRequest(String input) throws IOException {
            if (!process.isAlive()) {
                throw new IOException("QuickJS bridge exited: " + stderr());
            }
            stdin.write(input);
            stdin.newLine();
            stdin.flush();
        }

        private String readResponse(long timeoutMillis) throws InterruptedException {
            return stdoutLines.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        private synchronized void destroy() {
            try {
                stdin.close();
            } catch (IOException ignored) {
                // Best-effort cleanup.
            }
            process.destroy();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }

        private String stderr() {
            synchronized (stderr) {
                return stderr.toString();
            }
        }

        private static void startReader(final String name,
                                        final InputStream input,
                                        final BlockingQueue<String> lines,
                                        final StringBuilder capture) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (lines != null) {
                                lines.offer(line);
                            }
                            if (capture != null) {
                                synchronized (capture) {
                                    if (capture.length() > 8192) {
                                        capture.delete(0, capture.length() - 8192);
                                    }
                                    capture.append(line).append('\n');
                                }
                            }
                        }
                    } catch (IOException ignored) {
                        // The owning runtime observes process failure on write/read.
                    }
                }
            }, name);
            thread.setDaemon(true);
            thread.start();
        }
    }
}
