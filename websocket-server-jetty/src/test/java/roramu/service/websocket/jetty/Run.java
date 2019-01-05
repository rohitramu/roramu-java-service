package roramu.service.websocket.jetty;

public class Run {
    public static void main(String[] args) {
        JettyWebSocketServerLifecycleManager<TestService> server = Run.startService();
        server.join();
    }

    public static JettyWebSocketServerLifecycleManager<TestService> startService() {
        JettyWebSocketServerLifecycleManager<TestService> server = new JettyWebSocketServerLifecycleManager<>(TestService.class);
        server.start();

        return server;
    }
}
