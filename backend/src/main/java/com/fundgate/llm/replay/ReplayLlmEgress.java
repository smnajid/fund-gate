package com.fundgate.llm.replay;

import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.llm.LlmEgress;
import com.fundgate.llm.LlmRequest;
import com.fundgate.llm.LlmResponse;
import com.fundgate.llm.UnrecordedLlmRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
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

    /** Every failure names the file only: a recording's content is a prompt, and only the Check Record keeps prompts. */
    private static Map<LlmRequest, LlmResponse> load(Path recordings) {
        if (!Files.isDirectory(recordings)) {
            return Map.of();
        }
        Map<LlmRequest, LlmResponse> answers = new HashMap<>();
        Map<LlmRequest, Path> recordedIn = new HashMap<>();
        for (Path file : recordingFiles(recordings)) {
            Recording recording = read(file);
            if (holdsAPortfolioReference(recording)) {
                throw new IllegalStateException("The recording " + file.getFileName() + " holds a Portfolio Reference."
                        + " Only Privacy Gate output, with Portfolio Ids, may be recorded (ADR 0004)");
            }
            Path earlier = recordedIn.putIfAbsent(recording.request(), file);
            if (earlier != null) {
                throw new IllegalStateException("The recordings " + earlier.getFileName() + " and " + file.getFileName()
                        + " record the same request; keep one");
            }
            answers.put(recording.request(), recording.response());
        }
        return Map.copyOf(answers);
    }

    private static List<Path> recordingFiles(Path recordings) {
        try (Stream<Path> files = Files.list(recordings)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list the recordings in " + recordings, e);
        }
    }

    private static Recording read(Path file) {
        try {
            return JSON.readValue(file.toFile(), Recording.class);
        } catch (JacksonException e) {
            // The cause is dropped: Jackson's message can quote the recording's content
            throw new IllegalStateException("The recording " + file.getFileName() + " is not a valid recording");
        }
    }

    private static boolean holdsAPortfolioReference(Recording recording) {
        return Stream.of(recording.request().system(), recording.request().prompt(), recording.response().text())
                .anyMatch(text -> text != null && PortfolioReference.FORMAT.matcher(text).find());
    }
}
