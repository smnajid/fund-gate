package com.fundgate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The backend serves its OpenAPI spec, the one source of the Angular client (hackathon-kit.md, section 5).
 * The spec is saved to target/openapi.json, where the frontend's generate-api script reads it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiSpecTest {

    static final Path SPEC = Path.of("target/openapi.json");

    @LocalServerPort
    int port;

    @Test
    void the_backend_serves_its_openapi_spec() throws IOException, InterruptedException {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        // What the client generator needs: an OpenAPI 3 document with info, and a server URL that does not change per run
        assertThat(response.body()).contains("\"openapi\":\"3.", "\"info\":{", "\"servers\":[{\"url\":\"/\"");
        Files.writeString(SPEC, response.body());
    }
}
