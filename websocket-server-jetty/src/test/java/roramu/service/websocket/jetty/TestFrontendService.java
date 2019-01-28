package roramu.service.websocket.jetty;

import roramu.service.websocket.MessageHandlerManager;
import roramu.service.websocket.Response;
import roramu.service.websocket.WebSocketService;
import roramu.service.websocket.WebSocketServiceConfiguration;
import roramu.service.websocket.WebSocketServiceProxy;
import roramu.service.websocket.WebSocketServiceProxyManager;

import java.util.function.Function;

public class TestFrontendService extends WebSocketService {
    @Override
    protected WebSocketServiceConfiguration createConfiguration() {
        WebSocketServiceConfiguration config = super.createConfiguration();

        WebSocketServiceProxyManager proxies = config.getServiceProxies();
        proxies.set(WebSocketServiceProxy.create(TestBackendClient.SERVICE_NAME, TestBackendClient.class));

        MessageHandlerManager messageHandlers = config.getMessageHandlers();
        messageHandlers.set(TestMessageTypes.ECHO, Function.identity());
        messageHandlers.set(TestMessageTypes.GREET, TestFrontendService::greet);

        return config;
    }

    private static String greet(String name) {
        // Get backend service client
        TestBackendClient backendClient = WebSocketService.getServiceProxyClient(
            TestFrontendService.class,
            TestBackendClient.class,
            TestBackendClient.SERVICE_NAME);

        // Call backend
        Response<String> response = backendClient.greet(name).throwIfError();

        // Return response
        return response.getResponse();
    }
}
