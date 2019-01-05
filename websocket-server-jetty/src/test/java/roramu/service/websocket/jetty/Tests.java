package roramu.service.websocket.jetty;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import roramu.service.status.ServiceStatus;
import roramu.service.websocket.Response;
import roramu.service.websocket.WebSocketClientFactory;
import roramu.util.json.JsonUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Tests {
    @Test
    void startupTest() {
        JettyWebSocketServerLifecycleManager<TestService> server = Run.startService();
        server.stop();
    }

    @Test
    void echoTest() {
        JettyWebSocketServerLifecycleManager<TestService> server = Run.startService();

        TestClient client = WebSocketClientFactory.connect(TestClient.class, server.getWebSocketUri());
        Object request = "test";
        Response<Object> result = client.sendRequest(request);
        result.throwIfError();
        Assertions.assertEquals(result.getResponse(), request);

        server.stop();
    }

    @Test
    void httpStatusTest() throws IOException, InterruptedException {
        JettyWebSocketServerLifecycleManager<TestService> server = Run.startService();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
            HttpRequest
                .newBuilder(server.getHealthUri())
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        System.out.println(response.body());
        ServiceStatus statusResponse = JsonUtils.read(response.body(), ServiceStatus.class);
        Assertions.assertNotNull(statusResponse);

        server.stop();
    }
}
