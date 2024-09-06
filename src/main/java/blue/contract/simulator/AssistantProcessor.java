package blue.contract.simulator;

import blue.language.Blue;

public interface AssistantProcessor<Req, Res> {
    Res process(Req request, Blue blue);
}