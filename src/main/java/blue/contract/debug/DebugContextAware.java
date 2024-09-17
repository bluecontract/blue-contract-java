package blue.contract.debug;

public interface DebugContextAware {
    DebugContext getDebugContext();
    void setDebugContext(DebugContext debugContext);
}