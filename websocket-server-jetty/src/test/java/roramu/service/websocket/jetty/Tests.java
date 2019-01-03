package roramu.service.websocket.jetty;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import roramu.service.websocket.Response;
import roramu.service.websocket.WebSocketClientFactory;

public class Tests {
    @Test
    void startupTest() {
        JettyWebSocketServerLifecycleManager<TestService> server = new JettyWebSocketServerLifecycleManager<>(TestService.class);
        server.start();
        server.stop();
    }

    @Test
    void echoTest() {
        JettyWebSocketServerLifecycleManager<TestService> server = new JettyWebSocketServerLifecycleManager<>(TestService.class);
        server.start();

        TestClient client = WebSocketClientFactory.connect(TestClient.class, server.getUri());
        Object request = "test";
        Response<Object> result = client.sendRequest(request);
        result.throwIfError();
        Assertions.assertEquals(result.getResponse(), request);

        server.stop();
    }
}
