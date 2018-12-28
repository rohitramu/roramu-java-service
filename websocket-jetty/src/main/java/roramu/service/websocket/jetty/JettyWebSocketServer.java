package roramu.service.websocket.jetty;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import roramu.service.websocket.WebSocketHandshakeFilter;
import roramu.service.websocket.WebSocketService;
import roramu.util.net.NetworkUtils;

import javax.servlet.*;
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
    private static final String LOCALHOST_IP = "127.0.0.1";

    public Server start(String route, int port, Class<T> implementation) {
        if (route == null) {
            throw new IllegalArgumentException("'route' cannot be null");
        }
        if (implementation == null) {
            throw new NullPointerException("'implementation' cannot be null");
        }
        return this.start(route, port, WebSocketService.getDefaultConfig(implementation, route));
    }

    public Server start(String route, int port, Class<T> implementation, WebSocketHandshakeFilter handshakeFilter) {
        if (implementation == null) {
            throw new NullPointerException("'implementation' cannot be null");
        }
        return this.start(route, port, WebSocketService.getDefaultConfig(implementation, route), handshakeFilter);
    }

    public Server start(String route, int port, Class<T> implementation, ServerEndpointConfig.Configurator configurator) {
        if (implementation == null) {
            throw new NullPointerException("'implementation' cannot be null");
        }
        return this.start(route, port, WebSocketService.getDefaultConfig(implementation, route, configurator), null);
    }

    public Server start(String route, int port, ServerEndpointConfig config) {
        if (route == null) {
            throw new IllegalArgumentException("'route' cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("'config' cannot be null");
        }
        return this.start(route, port, config, null);
    }

    public Server start(Class<T> implementation, String route, int port, ServerEndpointConfig.Configurator configurator, WebSocketHandshakeFilter handshakeFilter) {
        if (route == null) {
            throw new IllegalArgumentException("'route' cannot be null");
        }
        if (implementation == null) {
            throw new NullPointerException("'implementation' cannot be null");
        }
        return this.start(route, port, WebSocketService.getDefaultConfig(implementation, route, configurator), handshakeFilter);
    }

    /**
     * Starts a {@link WebSocketService} and returns the {@link Server} instance.
     * @param route The route that the service will be exposed on.
     * @param port The port that the server should listen on.  If this is null, a randomly allocated ephemeral port is used.
     * @param config The server's config.
     * @param handshakeFilter The handshake filter.
     * @return The {@link Server} instance that the service is running in.
     */
    public Server start(String route, Integer port, ServerEndpointConfig config, WebSocketHandshakeFilter handshakeFilter) {
        if (route == null) {
            throw new IllegalArgumentException("'route' cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("'config' cannot be null");
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

        // Set up a connector to always use the localhost address
        ServerConnector connector = new ServerConnector(server);
        connector.setHost(LOCALHOST_IP);
        connector.setPort(port);
        server.addConnector(connector);

        // Setup the basic application "context" for this application at "/"
        // This is also known as the handler tree (in jetty speak)
        ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        contextHandler.setContextPath("/");
        if (handshakeFilter != null) {
            Filter filter = createFilter(handshakeFilter);
            contextHandler.addFilter(new FilterHolder(filter), route, EnumSet.allOf(DispatcherType.class));
        }
        server.setHandler(contextHandler);

        try {
            // Initialize javax.websocket layer
            ServerContainer container = WebSocketServerContainerInitializer.configureContext(contextHandler);

            // Add WebSocket endpoint to javax.websocket layer
            container.addEndpoint(config);

            server.start();

            System.out.println("Started Jetty WebSocket server at '" + server.getURI().toString() + "' using service implementation '" + config.getEndpointClass().getName() + "'");
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
