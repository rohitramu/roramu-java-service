package roramu.service.websocket.jetty;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import roramu.service.status.ServiceStatus;
import roramu.service.websocket.Response;
import roramu.service.websocket.WebSocketClient;
import roramu.service.websocket.WebSocketService;
import roramu.service.websocket.WebSocketServiceProxy;
import roramu.service.websocket.WebSocketServiceProxyManager;
import roramu.util.json.JsonUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Tests {
    JettyWebSocketServerLifecycleManager<TestFrontendService> frontendService;
    JettyWebSocketServerLifecycleManager<TestBackendService> backendService;

    TestFrontendClient frontendClient;
    TestBackendClient backendClient;

    @BeforeEach
    void beforeTest() {
        // Frontend service/client
        frontendService = new JettyWebSocketServerLifecycleManager<>(TestFrontendService.class);
        frontendService.start();
        frontendClient = WebSocketClient.connect(TestFrontendClient.class, frontendService.getWebSocketUri());

        // Backend service/client
        backendService = new JettyWebSocketServerLifecycleManager<>(TestBackendService.class);
        backendService.start();
        backendClient = WebSocketClient.connect(TestBackendClient.class, backendService.getWebSocketUri());

        // Override the frontend service's proxy to the backend service so it uses the correct URI
        WebSocketServiceProxyManager frontendProxies = WebSocketService.getServiceConfiguration(TestFrontendService.class).getServiceProxies();
        frontendProxies.set(WebSocketServiceProxy.create(
            TestBackendClient.SERVICE_NAME,
            TestBackendClient.class,
            backendService.getWebSocketUri()));
    }

    @AfterEach
    void afterTest() {
        frontendClient.close();
        frontendService.stop();

        backendClient.close();
        backendService.stop();
    }

    @Test
    void frontendEcho() {
        Object request = "test";
        @SuppressWarnings("UnnecessaryLocalVariable")
        Object expectedResponse = request;

        Response<Object> result = frontendClient.echo(request);
        result.throwIfError();

        Assertions.assertEquals(expectedResponse, result.getResponse());
    }

    @Test
    void httpStatus() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> response = client.send(
            HttpRequest
                .newBuilder(frontendService.getHealthUri())
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

        System.out.println(response.body());
        ServiceStatus statusResponse = JsonUtils.read(response.body(), ServiceStatus.class);

        Assertions.assertNotNull(statusResponse);
    }

    @Test
    void backendGreeting() {
        String name = "World";
        String expectedResponse = "Hello, World!";

        Response<String> result = backendClient.greet(name);
        result.throwIfError();

        Assertions.assertEquals(expectedResponse, result.getResponse());
    }

    @Test
    void frontendDependency() {
        String name = "World";
        String expectedResponse = "Hello, World!";

        Response<String> result = frontendClient.greet(name);
        result.throwIfError();

        Assertions.assertEquals(expectedResponse, result.getResponse());
    }
}
