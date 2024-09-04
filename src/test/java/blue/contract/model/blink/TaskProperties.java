package blue.contract.model.blink;

import java.util.List;

public class TaskProperties {
    private List<ConversationEntry> conversation;
    private String assistantTimeline;

    public List<ConversationEntry> getConversation() {
        return conversation;
    }

    public TaskProperties conversation(List<ConversationEntry> conversation) {
        this.conversation = conversation;
        return this;
    }

    public String getAssistantTimeline() {
        return assistantTimeline;
    }

    public TaskProperties assistantTimeline(String assistantTimeline) {
        this.assistantTimeline = assistantTimeline;
        return this;
    }
}