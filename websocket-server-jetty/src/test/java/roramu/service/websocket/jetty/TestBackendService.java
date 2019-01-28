package roramu.service.websocket.jetty;

import roramu.service.websocket.MessageHandlerManager;
import roramu.service.websocket.WebSocketService;
import roramu.service.websocket.WebSocketServiceConfiguration;

public class TestBackendService extends WebSocketService {
    @Override
    protected WebSocketServiceConfiguration createConfiguration() {
        WebSocketServiceConfiguration config = super.createConfiguration();

        MessageHandlerManager messageHandlers = config.getMessageHandlers();
        messageHandlers.set(TestMessageTypes.GREET, TestBackendService::greet);

        return config;
    }

    private static String greet(String name) {
        if (name == null) {
            throw new IllegalArgumentException("'name' cannot be null");
        }

        return "Hello, " + name + "!";
    }
}
