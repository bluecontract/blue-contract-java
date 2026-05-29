package blue.coordination.processor.workflow;

import blue.language.model.Node;

final class WorkflowPatchEntry {
    private final String op;
    private final String path;
    private final Node val;

    WorkflowPatchEntry(String op, String path, Node val) {
        this.op = op;
        this.path = path;
        this.val = val;
    }

    String op() {
        return op;
    }

    String path() {
        return path;
    }

    Node val() {
        return val;
    }
}
