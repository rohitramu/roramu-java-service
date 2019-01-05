package roramu.service.websocket.jetty;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import roramu.service.http.HealthService;
import roramu.service.websocket.WebSocketHandshakeFilter;
import roramu.service.websocket.WebSocketService;
import roramu.util.net.NetworkUtils;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.util.EnumSet;

/**
 * An instance of a service running on an embedded Jetty server.
 *
 * @param <T> The type which implements a {@link WebSocketService}.
 */
public final class JettyWebSocketServer<T extends WebSocketService> {
    private static final String HOST_IP = "0.0.0.0";
    private static final String WEBSOCKET_ROUTE = "/";

    public Server start(String healthRoute, int port, Class<T> implementation) {
        return this.start(healthRoute, port, WebSocketService.getDefaultConfig(implementation, WEBSOCKET_ROUTE));
    }

    public Server start(String healthRoute, int port, Class<T> implementation, WebSocketHandshakeFilter handshakeFilter) {
        return this.start(healthRoute, port, WebSocketService.getDefaultConfig(implementation, WEBSOCKET_ROUTE), handshakeFilter);
    }

    public Server start(String healthRoute, int port, Class<T> implementation, ServerEndpointConfig.Configurator configurator) {
        return this.start(healthRoute, port, WebSocketService.getDefaultConfig(implementation, WEBSOCKET_ROUTE, configurator), null);
    }

    public Server start(String healthRoute, int port, ServerEndpointConfig config) {
        return this.start(healthRoute, port, config, null);
    }

    public Server start(String healthRoute, int port, Class<T> implementation, ServerEndpointConfig.Configurator configurator, WebSocketHandshakeFilter handshakeFilter) {
        return this.start(healthRoute, port, WebSocketService.getDefaultConfig(implementation, WEBSOCKET_ROUTE, configurator), handshakeFilter);
    }

    /**
     * Starts a {@link WebSocketService} and returns the {@link Server} instance.
     *
     * @param healthRoute The route that the service health endpoint will be exposed on.  If this is null, a health endpoint will not be created.
     * @param port The port that the server should listen on.  If this is null, a randomly allocated ephemeral port is used.
     * @param config The server's config.
     * @param handshakeFilter The handshake filter.
     * @return The {@link Server} instance that the service is running in.
     */
    public Server start(String healthRoute, Integer port, ServerEndpointConfig config, WebSocketHandshakeFilter handshakeFilter) {
        if (healthRoute != null && !healthRoute.startsWith("/")) {
            throw new IllegalArgumentException("'healthRoute' must start with a '/'");
        }
        if (config == null) {
            throw new NullPointerException("'config' cannot be null");
        }
        if (port == null) {
            port = 0;
        } else {
            NetworkUtils.validatePort(port);
        }

        // TODO: Find a better way to select these values
        //        int corePoolSize = Runtime.getRuntime().availableProcessors();
        //        int maxPoolSize = corePoolSize * 10;
        //        int threadKeepAliveTime = 5000;
        //        int queueLength = maxPoolSize * 2;
        //        ThreadPool serverThreadPool = new QueuedThreadPool(maxPoolSize, corePoolSize, threadKeepAliveTime, new LinkedBlockingQueue<>(queueLength));
        //        Server server = new Server(serverThreadPool);
        Server server = new Server();

        // Set up a connector to bind to every network adapter (i.e. localhost plus external)
        ServerConnector connector = new ServerConnector(server);
        connector.setHost(HOST_IP);
        connector.setPort(port);
        server.addConnector(connector);

        // Setup the basic application "context" for this application at "/"
        // This is also known as the handler tree (in jetty speak)
        ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        contextHandler.setContextPath(WEBSOCKET_ROUTE);

        // Set the context handler
        server.setHandler(contextHandler);

        // Add handshake filter for WebSocket connections
        if (handshakeFilter != null) {
            Filter filter = createFilter(handshakeFilter);
            contextHandler.addFilter(new FilterHolder(filter), WEBSOCKET_ROUTE, EnumSet.allOf(DispatcherType.class));
        }

        // Start the server
        try {
            // Initialize javax.websocket layer
            ServerContainer container = WebSocketServerContainerInitializer.configureContext(contextHandler);

            // Add WebSocket endpoint to javax.websocket layer
            container.addEndpoint(config);

            // Add HTTP health endpoint if required
            if (healthRoute != null) {
                contextHandler.addServlet(HealthService.class, healthRoute);
            }

            server.start();

            System.out.println("Started Jetty WebSocket server at '" + server.getURI().toString() + "' using service implementation '" + config.getEndpointClass().getCanonicalName() + "'");
        } catch (Throwable thr) {
            // TODO: log
            throw new RuntimeException(thr);
        }

        return server;
    }

    private Filter createFilter(WebSocketHandshakeFilter customFilter) {
        if (customFilter == null) {
            throw new NullPointerException("'customFilter' cannot be null");
        }

        Filter filter = new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                chain.doFilter(request, response);
                if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
                    HttpServletRequest httpRequest = (HttpServletRequest) request;
                    HttpServletResponse httpResponse = (HttpServletResponse) response;

                    customFilter.doFilter(httpRequest, httpResponse);
                }
            }

            @Override
            public void init(FilterConfig filterConfig) {

            }

            @Override
            public void destroy() {

            }
        };

        return filter;
    }
}
