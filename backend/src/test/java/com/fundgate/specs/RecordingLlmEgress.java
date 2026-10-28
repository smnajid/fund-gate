package com.fundgate.specs;

import com.fundgate.llm.LlmEgress;
import com.fundgate.llm.LlmRequest;
import com.fundgate.llm.LlmResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Wraps the app's LLM egress client to see every request that left, and to play the model being unreachable. */
public class RecordingLlmEgress implements LlmEgress {

    private final LlmEgress egress;
    private final List<LlmRequest> sent = new CopyOnWriteArrayList<>();
    private volatile boolean reachable = true;

    public RecordingLlmEgress(LlmEgress egress) {
        this.egress = egress;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        sent.add(request);
        if (!reachable) {
            throw new IllegalStateException("The model cannot be reached");
        }
        return egress.complete(request);
    }

    public List<LlmRequest> sent() {
        return List.copyOf(sent);
    }

    public void reachable(boolean reachable) {
        this.reachable = reachable;
    }

    /** Each scenario starts with nothing sent and the model reachable. */
    public void reset() {
        sent.clear();
        reachable = true;
    }
}
