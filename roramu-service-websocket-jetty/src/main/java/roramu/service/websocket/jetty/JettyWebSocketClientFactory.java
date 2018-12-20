package roramu.service.websocket.jetty;

import roramu.service.websocket.WebSocketClient;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.util.function.Supplier;

public final class JettyWebSocketClientFactory {
    public static <T extends WebSocketClient> T connect(Class<T> implementation, URI path, Supplier<T> implConstructor) {
        return connect(implementation, path, implConstructor, WebSocketClient.getDefaultConfig());
    }

    public static <T extends WebSocketClient> T connect(Class<T> implementation, URI path, Supplier<T> implementationFactory, ClientEndpointConfig.Configurator configurator) {
        return connect(implementation, path, implementationFactory, WebSocketClient.getDefaultConfig(configurator));
    }

    public static <T extends WebSocketClient> T connect(Class<T> implementation, URI path, Supplier<T> implementationFactory, ClientEndpointConfig config) {
        if (implementation == null) {
            throw new NullPointerException("'implementation' cannot be null");
        }
        if (path == null) {
            throw new NullPointerException("'path' cannot be null");
        }
        if (implementationFactory == null) {
            throw new NullPointerException("'implementationFactory' cannot be null");
        }
        if (config == null) {
            throw new NullPointerException("'config' cannot be null");
        }

        Session session;
        try {
            // Initialize javax.websocket layer
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();

            // Add WebSocket endpoint to javax.websocket layer
            session = container.connectToServer(implementation, config, path);
        } catch (DeploymentException | IOException ex) {
            // TODO: log
            throw new RuntimeException("Could not connect to server", ex);
        }

        // Create the websocket instance that can manage the session
        T webSocketClient = implementationFactory.get();
        webSocketClient.setSession(session);

        return webSocketClient;
    }
}
