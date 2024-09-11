package blue.contract.model.blink;

public class SampleTaskProperties {
    private String assistantTimeline;
    private String fen;

    public String getAssistantTimeline() {
        return assistantTimeline;
    }

    public SampleTaskProperties assistantTimeline(String assistantTimeline) {
        this.assistantTimeline = assistantTimeline;
        return this;
    }

    public String getFen() {
        return fen;
    }

    public SampleTaskProperties fen(String fen) {
        this.fen = fen;
        return this;
    }
}