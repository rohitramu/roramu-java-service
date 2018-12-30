package roramu.service.websocket.jetty;

import roramu.service.websocket.WebSocketClient;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.function.Supplier;

public final class JettyWebSocketClientFactory {
    /**
     * Creates a new connection given a client implementation and the server URI.  The default constructor will be used
     * to construct an instance of this client implementation.  Do not use this overload if the client implementation
     * does not have a default constructor.
     *
     * @param implementation The {@link WebSocketClient} implementation.
     * @param path The path to the WebSocket server to connect to.
     * @param <T> The concrete type of the {@link WebSocketClient} implementation.
     * @return An instance of the client implementation which is attached to a WebSocket session for the given server path.
     */
    public static <T extends WebSocketClient> T connect(Class<T> implementation, URI path) {
        if (implementation == null) {
            throw new NullPointerException("'implementation' cannot be null");
        }
        if (path == null) {
            throw new NullPointerException("'path' cannot be null");
        }

        return connect(implementation, path, null);
    }

    /**
     * Creates a new connection given a client implementation and the server URI.  The supplied implementation factory
     * will be used to create an instance of this client implementation.  If the implementation factory is null, the
     * default constructor will be used to construct the instance of the client implementation.
     *
     * @param implementation The {@link WebSocketClient} implementation.
     * @param path The path to the WebSocket server to connect to.
     * @param implementationFactory The method which constructs the client implementation.
     * @param <T> The concrete type of the {@link WebSocketClient} implementation.
     * @return An instance of the client implementation which is attached to a WebSocket session for the given server path.
     */
    public static <T extends WebSocketClient> T connect(Class<T> implementation, URI path, Supplier<T> implementationFactory) {
        if (implementation == null) {
            throw new NullPointerException("'implementation' cannot be null");
        }
        if (path == null) {
            throw new NullPointerException("'path' cannot be null");
        }

        return connect(implementation, path, implementationFactory, WebSocketClient.getDefaultConfig());
    }

    /**
     * Creates a new connection given a client implementation and the server URI.  The supplied implementation factory
     * will be used to create an instance of this client implementation.  If the implementation factory is null, the
     * default constructor will be used to construct the instance of the client implementation.
     *
     * @param implementation The {@link WebSocketClient} implementation.
     * @param path The path to the WebSocket server to connect to.
     * @param implementationFactory The method which constructs the client implementation.
     * @param configurator The {@link ClientEndpointConfig.Configurator} to use.
     * @param <T> The concrete type of the {@link WebSocketClient} implementation.
     * @return An instance of the client implementation which is attached to a WebSocket session for the given server path.
     */
    public static <T extends WebSocketClient> T connect(Class<T> implementation, URI path, Supplier<T> implementationFactory, ClientEndpointConfig.Configurator configurator) {
        if (implementation == null) {
            throw new NullPointerException("'implementation' cannot be null");
        }
        if (path == null) {
            throw new NullPointerException("'path' cannot be null");
        }
        if (configurator == null) {
            throw new NullPointerException("'configurator' cannot be null");
        }

        return connect(implementation, path, implementationFactory, WebSocketClient.getDefaultConfig(configurator));
    }

    /**
     * Creates a new connection given a client implementation and the server URI.  The supplied implementation factory
     * will be used to create an instance of this client implementation.  If the implementation factory is null, the
     * default constructor will be used to construct the instance of the client implementation.
     *
     * @param implementation The {@link WebSocketClient} implementation.
     * @param path The path to the WebSocket server to connect to.
     * @param implementationFactory The method which constructs the client implementation.
     * @param config The {@link ClientEndpointConfig} to use.  This provides even more configurability than a {@link ClientEndpointConfig.Configurator}.
     * @param <T> The concrete type of the {@link WebSocketClient} implementation.
     * @return An instance of the client implementation which is attached to a WebSocket session for the given server path.
     */
    public static <T extends WebSocketClient> T connect(Class<T> implementation, URI path, Supplier<T> implementationFactory, ClientEndpointConfig config) {
        if (implementation == null) {
            throw new NullPointerException("'implementation' cannot be null");
        }
        if (path == null) {
            throw new NullPointerException("'path' cannot be null");
        }
        if (config == null) {
            throw new NullPointerException("'config' cannot be null");
        }

        // Construct an instance of the client
        T webSocketClient;
        if (implementationFactory != null) {
            // Implementation factory was provided, so use it to construct an instance
            webSocketClient = implementationFactory.get();
        } else {
            // Try to call the default constructor since an implementation factory was not provided
            try {
                webSocketClient = implementation.getConstructor().newInstance();
            } catch (NoSuchMethodException ex) {
                throw new IllegalArgumentException("The class '" + implementation.getCanonicalName() + "' does not have a default constructor - please provide the implementationFactory argument", ex);
            } catch (InstantiationException ex) {
                throw new IllegalArgumentException("The class '" + implementation.getCanonicalName() + "' is abstract, and cannot be instantiated with the default constructor", ex);
            } catch (IllegalAccessException ex) {
                throw new IllegalArgumentException("The class '" + implementation.getCanonicalName() + "' has a default constructor which cannot by accessed", ex);
            } catch (InvocationTargetException ex) {
                throw new IllegalArgumentException("The class '" + implementation.getCanonicalName() + "' threw an exception when the default constructor was invoked", ex);
            }
        }

        // Create a WebSocket session
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

        // Manage the session using the created instance of the client
        webSocketClient.setSession(session);

        return webSocketClient;
    }
}
