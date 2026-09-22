package com.pensionat.ci;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RailwayDeployTest {
    private static final String URL = "https://example.test/health";
    private static final String IMAGE = "example/service:build-42.1";
    private static final String INSTANCE = """
            {"serviceInstance":{"healthcheckPath":"/health","source":{"image":"old-image"}}}
            """;
    private final Fake deploy = new Fake();

    @Test void deploysPublishedImageAndTracksReturnedId() throws Exception {
        deploy.replies.addAll(List.of(INSTANCE, "{\"serviceInstanceUpdate\":true}",
                "{\"serviceInstanceDeployV2\":\"new-id\"}", status("DEPLOYING", false, "new-id"),
                status("SUCCESS", false, "new-id")));
        deploy.deploy("service", "environment", URL, IMAGE);
        assertEquals(List.of(RailwayDeploy.INSTANCE, RailwayDeploy.UPDATE, RailwayDeploy.DEPLOY,
                RailwayDeploy.STATUS, RailwayDeploy.STATUS), deploy.queries);
        assertEquals(Map.of("serviceId", "service", "environmentId", "environment", "image", IMAGE),
                deploy.variables.get(1));
        assertEquals(List.of(Map.of("id", "new-id"), Map.of("id", "new-id")), deploy.variables.subList(3, 5));
        assertEquals(1, deploy.healthCalls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "CRASHED", "REMOVED", "REMOVING", "SKIPPED", "SLEEPING", "NEEDS_APPROVAL"})
    void failedDeploymentCannotPass(String status) {
        deploy.replies.add(status(status, false, "new-id"));
        assertThrows(IllegalStateException.class, () -> deploy.waitForDeployment("new-id", URL));
        assertEquals(0, deploy.healthCalls);
    }

    @Test void stoppedDeploymentCannotPass() {
        deploy.replies.add(status("SUCCESS", true, "new-id"));
        assertThrows(IllegalStateException.class, () -> deploy.waitForDeployment("new-id", URL));
        assertEquals(0, deploy.healthCalls);
    }

    @Test void previousDeploymentCannotPass() {
        deploy.replies.add(status("SUCCESS", false, "old-id"));
        assertThrows(IllegalStateException.class, () -> deploy.waitForDeployment("new-id", URL));
        assertEquals(0, deploy.healthCalls);
    }

    @Test void unhealthyEndpointTimesOut() {
        deploy.replies.add(status("SUCCESS", false, "new-id"));
        deploy.healthy = false;
        assertThrows(IllegalStateException.class, () -> deploy.waitForDeployment("new-id", URL));
        assertEquals(120, deploy.healthCalls);
    }

    @Test void retriesHealthUntilUp() throws Exception {
        deploy.replies.add(status("SUCCESS", false, "new-id"));
        deploy.healthFailures = 1;
        deploy.waitForDeployment("new-id", URL);
        assertEquals(2, deploy.healthCalls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"example/service", "example/service:", "example/service:latest", "registry:5000/service"})
    void unversionedImagePreventsApiCalls(String image) {
        assertThrows(IllegalArgumentException.class, () -> deploy.deploy("service", "environment", URL, image));
        assertTrue(deploy.queries.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"serviceInstance\":{\"source\":{\"image\":\"old\"}}}",
            "{\"serviceInstance\":{\"healthcheckPath\":\"/health\",\"source\":null}}"})
    void missingConfigurationPreventsMutation(String instance) {
        deploy.replies.add(instance);
        assertThrows(IllegalStateException.class, () -> deploy.deploy("service", "environment", URL, IMAGE));
        assertEquals(List.of(RailwayDeploy.INSTANCE), deploy.queries);
    }

    @Test void rejectedImagePreventsDeployment() {
        deploy.replies.addAll(List.of(INSTANCE, "{\"serviceInstanceUpdate\":false}"));
        assertThrows(IllegalStateException.class, () -> deploy.deploy("service", "environment", URL, IMAGE));
        assertEquals(List.of(RailwayDeploy.INSTANCE, RailwayDeploy.UPDATE), deploy.queries);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "\"\""})
    void missingDeploymentIdFails(String id) {
        deploy.replies.addAll(List.of(INSTANCE, "{\"serviceInstanceUpdate\":true}",
                "{\"serviceInstanceDeployV2\":" + id + "}"));
        assertThrows(IllegalStateException.class, () -> deploy.deploy("service", "environment", URL, IMAGE));
        assertEquals(0, deploy.healthCalls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"errors\":[{\"message\":\"denied\"}]}", "{\"data\":{}}", "null", "not-json",
            "{\"data\":{\"deployment\":{}},\"errors\":[{\"message\":\"partial failure\"}]}"})
    void invalidApiResponseFails(String body) {
        assertThrows(IOException.class, () -> RailwayDeploy.apiData(body));
    }

    @Test void parsesSuccessfulApiResponse() throws Exception {
        assertTrue(RailwayDeploy.apiData("{\"data\":{\"serviceInstanceUpdate\":true}}")
                .path("serviceInstanceUpdate").asBoolean());
    }

    @Test void healthRequiresHttp200AndUp() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var client = new RailwayDeploy();
        try {
            server.start();
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/health";
            for (var sample : List.of(Map.entry(200, "{\"status\":\"UP\"}"), Map.entry(200, "{\"status\":\"DOWN\"}"),
                    Map.entry(200, "not-json"), Map.entry(200, "null"), Map.entry(503, "{\"status\":\"UP\"}"))) {
                var context = server.createContext("/health", exchange -> {
                    byte[] body = sample.getValue().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(sample.getKey(), body.length);
                    try (var output = exchange.getResponseBody()) { output.write(body); }
                });
                assertEquals(sample.getKey() == 200 && sample.getValue().contains("UP"), client.healthIsUp(url));
                server.removeContext(context);
            }
            server.stop(0);
            assertFalse(client.healthIsUp(url));
        } finally { server.stop(0); }
    }

    private static String status(String state, boolean stopped, String id) {
        return "{\"deployment\":{\"id\":\"%s\",\"status\":\"%s\",\"deploymentStopped\":%s}}".formatted(id, state, stopped);
    }

    private static class Fake extends RailwayDeploy {
        final ArrayDeque<String> replies = new ArrayDeque<>();
        final List<String> queries = new ArrayList<>();
        final List<Map<String, String>> variables = new ArrayList<>();
        int healthCalls, healthFailures;
        boolean healthy = true;
        long elapsed;
        @Override JsonNode api(String query, Map<String, String> vars) throws Exception {
            queries.add(query);
            variables.add(vars);
            return JSON.readTree(replies.size() > 1 ? replies.removeFirst() : replies.getFirst());
        }
        @Override boolean healthIsUp(String url) { return ++healthCalls > healthFailures && healthy; }
        @Override long now() { return elapsed; }
        @Override void pause() { elapsed += Duration.ofSeconds(5).toNanos(); }
    }
}
