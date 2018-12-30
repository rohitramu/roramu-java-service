package roramu.service.websocket.jetty;

import org.eclipse.jetty.server.Server;
import roramu.service.websocket.WebSocketServerLifecycleManager;
import roramu.service.websocket.WebSocketService;

import java.net.URI;

public class JettyWebSocketServerLifecycleManager<T extends WebSocketService> implements WebSocketServerLifecycleManager {
    public static final String DEFAULT_ROUTE = "/";
    public static final int DEFAULT_PORT = 80;

    private final String route;
    private final int port;
    private final Class<T> serviceImplementation;

    private Server server = null;

    public JettyWebSocketServerLifecycleManager(Class<T> serviceImplementation, String route) {
        this(serviceImplementation, route, DEFAULT_PORT);
    }

    public JettyWebSocketServerLifecycleManager(Class<T> serviceImplementation, int port) {
        this(serviceImplementation, DEFAULT_ROUTE, port);
    }

    public JettyWebSocketServerLifecycleManager(Class<T> serviceImplementation, String route, int port) {
        if (serviceImplementation == null) {
            throw new NullPointerException("'serviceImplementation' cannot be null");
        }

        this.serviceImplementation = serviceImplementation;
        this.route = route;
        this.port = port;
    }

    @Override
    public void start() {
        if (this.isRunning()) {
            throw new IllegalStateException("The service is already running");
        }

        this.server = new JettyWebSocketServer<T>().start(this.route, this.port, this.serviceImplementation);
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

    public URI getUri() {
        if (!this.isRunning()) {
            throw new IllegalStateException("The service must be running to get its URI");
        }

        URI result = URI.create(this.server.getURI().toString()
            .replace("http://", "ws://")
            .replace("https://", "wss://"));

        return result;
    }
}
