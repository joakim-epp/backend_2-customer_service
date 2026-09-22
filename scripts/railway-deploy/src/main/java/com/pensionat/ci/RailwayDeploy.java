package com.pensionat.ci;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class RailwayDeploy {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String INSTANCE = """
            query($serviceId: String!, $environmentId: String!) {
              serviceInstance(serviceId: $serviceId, environmentId: $environmentId) {
                healthcheckPath source { image }
              }
            }
            """;
    static final String UPDATE = """
            mutation($serviceId: String!, $environmentId: String!, $image: String!) {
              serviceInstanceUpdate(serviceId: $serviceId, environmentId: $environmentId,
                                    input: {source: {image: $image}})
            }
            """;
    static final String DEPLOY = """
            mutation($serviceId: String!, $environmentId: String!) {
              serviceInstanceDeployV2(serviceId: $serviceId, environmentId: $environmentId)
            }
            """;
    static final String STATUS = """
            query($id: String!) { deployment(id: $id) { id status deploymentStopped } }
            """;
    static final Set<String> FAILURES = Set.of(
            "FAILED", "CRASHED", "REMOVED", "REMOVING", "SKIPPED", "SLEEPING", "NEEDS_APPROVAL");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public static void main(String[] args) {
        try {
            new RailwayDeploy().deploy(env("RAILWAY_SERVICE_ID"), env("RAILWAY_ENVIRONMENT_ID"),
                    env("RAILWAY_HEALTH_URL"), env("RAILWAY_IMAGE"));
        } catch (Exception error) {
            System.err.println("Deployment verification failed: " + error.getMessage());
            System.exit(1);
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }

    void deploy(String service, String environment, String healthUrl, String image) throws Exception {
        if (!image.matches("[^\\s@]+:[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}") || image.endsWith(":latest")) {
            throw new IllegalArgumentException("An explicit published image version is required");
        }
        Map<String, String> variables = Map.of("serviceId", service, "environmentId", environment);
        JsonNode instance = api(INSTANCE, variables).path("serviceInstance");
        if (instance.path("healthcheckPath").asText("").isBlank()) {
            throw new IllegalStateException("Configure a Railway health check before deploying");
        }
        if (instance.path("source").path("image").asText("").isBlank()) {
            throw new IllegalStateException("Expected a Railway service configured with a Docker image");
        }
        if (!api(UPDATE, Map.of("serviceId", service, "environmentId", environment, "image", image))
                .path("serviceInstanceUpdate").asBoolean()) {
            throw new IllegalStateException("Railway did not accept the published image version");
        }
        System.out.println("Deploying " + image);
        JsonNode id = api(DEPLOY, variables).path("serviceInstanceDeployV2");
        if (!id.isTextual() || id.asText().isBlank()) {
            throw new IllegalStateException("Railway did not return a deployment ID");
        }
        waitForDeployment(id.asText(), healthUrl);
    }

    void waitForDeployment(String id, String healthUrl) throws Exception {
        long deadline = now() + Duration.ofMinutes(10).toNanos();
        String previous = "";
        while (now() < deadline) {
            JsonNode deployment = api(STATUS, Map.of("id", id)).path("deployment");
            if (!id.equals(deployment.path("id").asText())) {
                throw new IllegalStateException("Railway returned a different deployment");
            }
            String status = deployment.path("status").asText();
            if (!status.equals(previous)) System.out.println("Deployment " + id + ": " + status);
            previous = status;
            if (FAILURES.contains(status) || deployment.path("deploymentStopped").asBoolean()) {
                throw new IllegalStateException("Deployment " + id + " failed: " + status);
            }
            if (status.equals("SUCCESS") && deployment.path("deploymentStopped").isBoolean()
                    && healthIsUp(healthUrl)) {
                System.out.println("Deployment " + id + " is healthy at " + healthUrl);
                return;
            }
            pause();
        }
        throw new IllegalStateException("Deployment " + id + " did not become healthy within 600s");
    }

    JsonNode api(String query, Map<String, String> variables) throws Exception {
        var output = Files.createTempFile("railway-api-", ".json");
        Process process = null;
        try {
            process = new ProcessBuilder("railway", "api", query, "--variables",
                    JSON.writeValueAsString(variables), "--compact")
                    .redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(45, TimeUnit.SECONDS)) throw new IOException("Railway API timed out");
            if (process.exitValue() != 0) throw new IOException("Railway CLI failed with exit " + process.exitValue());
            return apiData(Files.readString(output));
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(output);
        }
    }

    static JsonNode apiData(String body) throws IOException {
        JsonNode response = JSON.readTree(body);
        if (response == null || !response.path("data").isObject() || response.path("data").isEmpty()
                || (response.hasNonNull("errors") && !response.path("errors").isEmpty())) {
            throw new IOException("Railway API did not return successful data");
        }
        return response.path("data");
    }

    boolean healthIsUp(String url) throws InterruptedException {
        try {
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("Cache-Control", "no-cache").GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = JSON.readTree(response.body());
            return response.statusCode() == 200 && body != null && body.path("status").asText().equals("UP");
        } catch (IOException error) {
            return false;
        }
    }

    long now() { return System.nanoTime(); }
    void pause() throws InterruptedException { Thread.sleep(5000); }
}
