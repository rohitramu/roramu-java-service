package roramu.service.websocket.jetty;

import roramu.service.websocket.WebSocketHandshakeFilter;
import roramu.service.websocket.WebSocketService;
import roramu.util.net.NetworkUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

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
 * @param <T> The type which implements the service.
 */
public final class JettyWebSocketServer<T extends WebSocketService> {
    public Server start(Class<T> implementation, String route, int port) {
        return this.start(implementation, route, port, WebSocketService.getDefaultConfig(implementation, route));
    }

    public Server start(Class<T> implementation, String route, int port, WebSocketHandshakeFilter handshakeFilter) {
        return this.start(implementation, route, port, WebSocketService.getDefaultConfig(implementation, route), handshakeFilter);
    }

    public Server start(Class<T> implementation, String route, int port, ServerEndpointConfig.Configurator configurator) {
        return this.start(implementation, route, port, WebSocketService.getDefaultConfig(implementation, route, configurator), null);
    }

    public Server start(Class<T> implementation, String route, int port, ServerEndpointConfig config) {
        return this.start(implementation, route, port, config, null);
    }

    public Server start(Class<T> implementation, String route, int port, ServerEndpointConfig.Configurator configurator, WebSocketHandshakeFilter handshakeFilter) {
        return this.start(implementation, route, port, WebSocketService.getDefaultConfig(implementation, route, configurator), handshakeFilter);
    }

    public Server start(Class<T> implementation, String route, int port, ServerEndpointConfig config, WebSocketHandshakeFilter handshakeFilter) {
        if (implementation == null) {
            throw new NullPointerException("'implementation' cannot be null");
        }
        if (config == null) {
            throw new NullPointerException("'config' cannot be null");
        }
        NetworkUtils.validatePort(port, true);

        // TODO: Find a better way to select these values
//        int corePoolSize = Runtime.getRuntime().availableProcessors();
//        int maxPoolSize = corePoolSize * 10;
//        int threadKeepAliveTime = 5000;
//        int queueLength = maxPoolSize * 2;
//        ThreadPool serverThreadPool = new QueuedThreadPool(maxPoolSize, corePoolSize, threadKeepAliveTime, new LinkedBlockingQueue<>(queueLength));
//        Server server = new Server(serverThreadPool);
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
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

            System.out.println("Started Jetty WebSocket server at '" + server.getURI().toString() + "' using implementation '" + implementation.getName() + "'");

            return server;
        } catch (Throwable thr) {
            // TODO: log
            throw new RuntimeException(thr);
        }
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
