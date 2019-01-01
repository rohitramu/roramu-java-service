package roramu.service.websocket.jetty;

import org.junit.jupiter.api.Test;

public class Jetty {
    @Test
    void startupTest() {
        JettyWebSocketServerLifecycleManager<EchoService> server = new JettyWebSocketServerLifecycleManager<>(EchoService.class);
        server.start();
        server.stop();
    }
}
