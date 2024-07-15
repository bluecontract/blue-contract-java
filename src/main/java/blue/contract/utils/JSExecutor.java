package blue.contract.utils;

import blue.contract.model.ContractInstance;
import blue.contract.model.ContractProcessingContext;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.NodeToObject;
import blue.language.utils.limits.PathLimits;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static blue.language.utils.NodeToObject.Strategy.SIMPLE;

public class JSExecutor {

    private final Node contract;
    private final Blue blue;
    private ContractProcessingContext contractProcessingContext;

    public JSExecutor(Node contract, Blue blue) {
        this.contract = contract;
        this.blue = blue;
    }

    public JSExecutor(ContractProcessingContext context) {
        this(context.getContract(), context.getBlue());
        this.contractProcessingContext = context;
    }

    public Object executeScript(String code, Map<String, Object> bindings) throws IOException {
        Source source = Source.newBuilder("js", code, "jsCode").build();
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true)
                .option("engine.WarnInterpreterOnly", "false")
                .option("log.file", "/dev/null")
                .build()) {

            bindings.forEach(context.getBindings("js")::putMember);

            Value result = context.eval(source);
            return valueToObject(result);
        }
    }

    public Object contract(String path) {
        blue.extend(contract, PathLimits.withSinglePath(path));
        Object result = contract.get(path, new Function<Node, Node>() {

            @Override
            public Node apply(Node node) {
                if (node.getType() != null && "Local Contract".equals(node.getType().getName())) {
                    BigInteger id = (BigInteger) node.getProperties().get("id").getValue();
                    return contractProcessingContext.getContractInstances().stream()
                            .filter(instance -> instance.getId() == id.intValue())
                            .findFirst()
                            .map(ContractInstance::getContract)
                            .orElse(null);
                }
                return null;
            }

            @Override
            public <V> Function<V, Node> compose(Function<? super V, ? extends Node> before) {
                return Function.super.compose(before);
            }

            @Override
            public <V> Function<Node, V> andThen(Function<? super Node, ? extends V> after) {
                return Function.super.andThen(after);
            }
        });
        if (result instanceof Node) {
            result = NodeToObject.get((Node) result, SIMPLE);
        }
        return result;
    }

    public Object valueToObject(Value value) {
        if (value.isHostObject()) {
            return value.asHostObject();
        } else if (value.isString()) {
            return value.asString();
        } else if (value.isNumber()) {
            double number = value.asDouble();
            if (number == Math.floor(number)) {
                return BigInteger.valueOf((long) number);
            } else {
                return BigDecimal.valueOf(number);
            }
        } else if (value.isBoolean()) {
            return value.asBoolean();
        } else if (value.hasArrayElements()) {
            return IntStream.range(0, (int) value.getArraySize())
                    .mapToObj(i -> valueToObject(value.getArrayElement(i)))
                    .collect(Collectors.toList());
        } else if (value.hasMembers()) {
            Map<String, Object> resultMap = new HashMap<>();
            for (String key : value.getMemberKeys()) {
                resultMap.put(key, valueToObject(value.getMember(key)));
            }
            return resultMap;
        } else {
            return value;
        }
    }

}