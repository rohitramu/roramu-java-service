package roramu.service.websocket.jetty;

import org.eclipse.jetty.server.Server;
import roramu.service.websocket.WebSocketServerLifecycleManager;
import roramu.service.websocket.WebSocketService;

import java.net.URI;

public class JettyWebSocketServerLifecycleManager<T extends WebSocketService> implements WebSocketServerLifecycleManager {
    public static final String DEFAULT_HEALTH_ROUTE = "/";
    public static final int DEFAULT_PORT = 0; // pick any available ephemeral port by default

    private final String healthRoute;
    private final int port;
    private final Class<T> serviceImplementation;

    private Server server = null;

    public JettyWebSocketServerLifecycleManager(Class<T> serviceImplementation) {
        this(serviceImplementation, DEFAULT_HEALTH_ROUTE, DEFAULT_PORT);
    }

    public JettyWebSocketServerLifecycleManager(Class<T> serviceImplementation, String healthRoute) {
        this(serviceImplementation, healthRoute, DEFAULT_PORT);
    }

    public JettyWebSocketServerLifecycleManager(Class<T> serviceImplementation, int port) {
        this(serviceImplementation, DEFAULT_HEALTH_ROUTE, port);
    }

    public JettyWebSocketServerLifecycleManager(Class<T> serviceImplementation, String healthRoute, int port) {
        if (serviceImplementation == null) {
            throw new NullPointerException("'serviceImplementation' cannot be null");
        }

        this.serviceImplementation = serviceImplementation;
        this.healthRoute = healthRoute;
        this.port = port;
    }

    @Override
    public void start() {
        if (this.isRunning()) {
            throw new IllegalStateException("The service is already running");
        }

        this.server = new JettyWebSocketServer<T>().start(this.healthRoute, this.port, this.serviceImplementation);
    }

    @Override
    public void stop() {
        if (!this.isRunning()) {
            throw new IllegalStateException("The service is already stopped");
        }

        try {
            this.server.setDumpBeforeStop(true);
            this.server.stop();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to stop service '" + this.serviceImplementation.getCanonicalName() + "'");
        } finally {
            if (this.server.isStopped()) {
                this.server = null;
            }
        }
    }

    @Override
    public boolean isRunning() {
        return this.server != null;
    }

    @Override
    public void join() {
        if (!this.isRunning()) {
            throw new IllegalStateException("The service must be running before it can be joined");
        }

        try {
            this.server.join();
        } catch (InterruptedException ex) {
            throw new RuntimeException("The thread running service '" + this.serviceImplementation.getCanonicalName() + "' was interrupted");
        } finally {
            this.stop();
        }
    }

    public URI getHealthUri() {
        if (!this.isRunning()) {
            throw new IllegalStateException("The service must be running to get its HTTP URI");
        }

        URI result = this.server.getURI().resolve(this.healthRoute);
        return result;
    }

    public URI getWebSocketUri() {
        if (!this.isRunning()) {
            throw new IllegalStateException("The service must be running to get its WebSocket URI");
        }

        URI result = URI.create(this.server.getURI().toString()
            .replace("http://", "ws://")
            .replace("https://", "wss://"));

        return result;
    }
}
