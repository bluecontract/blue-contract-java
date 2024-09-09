package blue.contract.model.blink;

import java.util.List;

public class TaskProperties {
    private List<ConversationEntry> conversation;

    public List<ConversationEntry> getConversation() {
        return conversation;
    }

    public TaskProperties conversation(List<ConversationEntry> conversation) {
        this.conversation = conversation;
        return this;
    }
}