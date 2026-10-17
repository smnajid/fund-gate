package fundeli5.rulecard.adapter.out.jev;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Ticket #39. Minimal client for TypeSafe's Jev ({@code POST /v1/systemone}). Public Fund Documentation
 * only: no Client Profile ever goes through here (ADR 0004). Key from {@code TYPESAFE_API_KEY}.
 */
public final class JevClient {

    private static final URI ENDPOINT = URI.create("https://api.typesafe.ai/v1/systemone");
    /** $0.042 per million input tokens; output tokens are free. */
    private static final double USD_PER_TOKEN = 0.042 / 1_000_000;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String key;
    private final String model;
    private final Semaphore inFlight;
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong requests = new AtomicLong();

    public JevClient(String model, int concurrency) {
        // checked on the first request, not here: cached rankings need no key
        this.key = System.getenv("TYPESAFE_API_KEY");
        this.model = model;
        this.inFlight = new Semaphore(concurrency);
    }

    public JsonMapper json() {
        return json;
    }

    public String model() {
        return model;
    }

    /** The request is too long for the model's context; the caller splits it. */
    public static final class TooLong extends RuntimeException {

        TooLong(String body) {
            super(body);
        }
    }

    /** Sends {state, questions} and returns the {@code answers} node. Retries 429/529/5xx with backoff. */
    public JsonNode ask(String state, ObjectNode questions) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("TYPESAFE_API_KEY is not set and this ranking is not cached");
        }
        ObjectNode body = json.createObjectNode();
        body.put("state", state);
        body.put("model", model);
        body.set("questions", questions);
        var request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        long backoff = 1000;
        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response;
            try {
                inFlight.acquire();
                try {
                    response = http.send(request, HttpResponse.BodyHandlers.ofString());
                } finally {
                    inFlight.release();
                }
            } catch (java.io.IOException e) {
                if (attempt >= 6) {
                    throw new IllegalStateException("Jev unreachable: " + e.getMessage(), e);
                }
                sleep(backoff);
                backoff *= 2;
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            int status = response.statusCode();
            if (status == 200) {
                var tree = json.readTree(response.body());
                inputTokens.addAndGet(tree.path("usage").path("input_tokens").asLong());
                requests.incrementAndGet();
                return tree.path("answers");
            }
            if (status == 400 && response.body().contains("max_tokens_exceeded")) {
                throw new TooLong(response.body());
            }
            if ((status == 429 || status == 529 || status >= 500) && attempt < 8) {
                sleep(backoff);
                backoff = Math.min(backoff * 2, 30_000);
                continue;
            }
            throw new IllegalStateException("Jev " + status + ": " + response.body());
        }
    }

    public String usage() {
        long tokens = inputTokens.get();
        return "%d requests, %,d input tokens, $%.4f".formatted(requests.get(), tokens, tokens * USD_PER_TOKEN);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
