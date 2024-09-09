package blue.contract.utils.anthropic.model;

public class Usage {
    private int input_tokens;
    private int output_tokens;

    public int getInput_tokens() {
        return input_tokens;
    }

    public Usage input_tokens(int input_tokens) {
        this.input_tokens = input_tokens;
        return this;
    }

    public int getOutput_tokens() {
        return output_tokens;
    }

    public Usage output_tokens(int output_tokens) {
        this.output_tokens = output_tokens;
        return this;
    }
}
