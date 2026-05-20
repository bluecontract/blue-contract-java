package blue.contract.processor.conversation.workflow;

import blue.bex.result.BexExecutionResult;
import blue.bex.value.BexNodeWriter;
import blue.bex.value.BexValue;
import blue.bex.value.BexValues;
import blue.language.model.Node;

final class ComputeResultEmitter {
    int emit(BexExecutionResult result, StepExecutionContext context) {
        BexValue events = result.value() != null ? result.value().get("events") : BexValues.undefined();
        if (events.isUndefined() || events.isNull()) {
            events = result.events().asValue();
        }
        if (events.isUndefined() || events.isNull()) {
            return 0;
        }
        if (!events.isList()) {
            context.processorContext().throwFatal("Compute result events must be a list");
            return 0;
        }
        if (events.size() == 0) {
            return 0;
        }
        int emitted = 0;
        for (int i = 0; i < events.size(); i++) {
            BexValue event = events.get(String.valueOf(i));
            if (event.isUndefined() || event.isNull()) {
                context.processorContext().throwFatal("Compute result events cannot contain undefined/null entries");
                return emitted;
            }
            if (!event.isObject()) {
                context.processorContext().throwFatal("Compute result events must contain object entries");
                return emitted;
            }
            Node eventNode = BexNodeWriter.toNode(event);
            context.processorContext().emitEvent(eventNode);
            emitted++;
        }
        return emitted;
    }
}
