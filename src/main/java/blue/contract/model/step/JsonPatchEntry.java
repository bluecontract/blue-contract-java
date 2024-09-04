package blue.contract.model.step;

import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class JsonPatchEntry {

    public enum Operation {
        add,
        remove,
        replace,
        move,
        copy,
        test
    }

    private Operation op;
    private String path;
    private Node val;


    public Operation getOp() {
        return op;
    }

    public JsonPatchEntry op(Operation op) {
        this.op = op;
        return this;
    }

    public String getPath() {
        return path;
    }

    public JsonPatchEntry path(String path) {
        this.path = path;
        return this;
    }

    public Node getVal() {
        return val;
    }

    public JsonPatchEntry val(Node val) {
        this.val = val;
        return this;
    }
}
