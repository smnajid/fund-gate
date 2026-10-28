package com.fundgate.llm.replay;

import com.fundgate.llm.LlmEgress;
import com.fundgate.llm.LlmRequest;
import com.fundgate.llm.LlmResponse;
import com.fundgate.llm.UnrecordedLlmRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serves recorded answers with no model call: for rehearsals, the specification, and the demo when the model is down.
 * One recording per file, {@code {"request": ..., "response": ...}}, matched on the request itself.
 */
public class ReplayLlmEgress implements LlmEgress {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Path recordings;
    private final Map<LlmRequest, LlmResponse> answers;

    public ReplayLlmEgress(Path recordings) {
        this.recordings = recordings;
        this.answers = load(recordings);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        LlmResponse answer = answers.get(request);
        if (answer == null) {
            // The prompt stays out of the message: it is logged, and only the Check Record keeps prompts
            throw new UnrecordedLlmRequest("No recorded answer for this " + request.model() + " request in " + recordings
                    + ". Record it with the live model, or check that the prompt has not changed.");
        }
        return answer;
    }

    private record Recording(LlmRequest request, LlmResponse response) {
    }

    private static Map<LlmRequest, LlmResponse> load(Path recordings) {
        if (!Files.isDirectory(recordings)) {
            return Map.of();
        }
        try (Stream<Path> files = Files.list(recordings)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".json"))
                    .map(file -> JSON.readValue(file.toFile(), Recording.class))
                    .collect(Collectors.toUnmodifiableMap(Recording::request, Recording::response));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the recordings in " + recordings, e);
        }
    }
}
