package roramu.service.websocket;

import roramu.service.status.ServiceStatus;
import roramu.util.async.AsyncUtils;
import roramu.util.exception.ExceptionUtils;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The base type representing a WebSocket service.
 */
public abstract class WebSocketService extends WebSocketEndpoint {
    // Keep-alive task
    private static final long PING_FREQUENCY = 30;
    private static final TimeUnit PING_FREQUENCY_TIME_UNIT = TimeUnit.SECONDS;
    private static final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread result = new Thread(runnable);
        result.setDaemon(true);
        result.setName("WebSocket keep-alive task");
        result.setUncaughtExceptionHandler((thread, thr) -> {
            //TODO: log
            System.err.println("WebSocket keep-alive task threw exception: " + thr.toString());
        });

        return result;
    });
    private static ScheduledFuture<?> keepAliveTaskFuture = null;

    // Sessions
    private static final Map<Class<? extends WebSocketService>, Set<Session>> sessions = new ConcurrentHashMap<>();

    // Service dependency proxies
    private static final long DEFAULT_TIMEOUT = 5;
    private static final TimeUnit DEFAULT_TIMEOUT_UNITS = TimeUnit.SECONDS;
    private static final int DEFAULT_MAX_RETRIES = 10;

    // Static constructor
    static {
        WebSocketService.startKeepAliveTask();
    }

    /**
     * Gets the configuration for a given service implementation.
     *
     * @param serviceImplementation The service implementation.
     * @return The configuration for the service implementation if one exists, otherwise null.
     */
    public static final WebSocketServiceConfiguration getServiceConfiguration(Class<? extends WebSocketService> serviceImplementation) {
        if (serviceImplementation == null) {
            throw new IllegalArgumentException("'serviceImplementation' cannot be null");
        }

        return (WebSocketServiceConfiguration) WebSocketEndpoint.getEndpointConfiguration(serviceImplementation);
    }

    /**
     * Gets the default {@link ServerEndpointConfig} object used for deploying a
     * service.
     *
     * @param impl The implementing service.
     * @param path The path of the service (must start with a '{@code /}' character).
     * @return The server endpoint configuration.
     */
    public static final ServerEndpointConfig getDefaultJavaxConfig(Class<? extends WebSocketService> impl, String path) {
        return getConfigBuilder(impl, path).build();
    }

    /**
     * Gets the default {@link ServerEndpointConfig} object used for deploying a
     * service.
     *
     * @param impl The implementing service.
     * @param path The path of the service (must start with a '{@code /}' character).
     * @param configurator The configurator implementation to be used in the
     * configuration. A configurator is most commonly used to intercept the
     * WebSocket handshake.
     * @return The server endpoint configuration.
     */
    public static final ServerEndpointConfig getDefaultJavaxConfig(Class<? extends WebSocketService> impl, String path, ServerEndpointConfig.Configurator configurator) {
        if (configurator == null) {
            throw new NullPointerException("'configurator' cannot be null");
        }

        return getConfigBuilder(impl, path)
            .configurator(configurator)
            .build();
    }

    /**
     * Gets the builder for a {@link ServerEndpointConfig} with the default
     * options set.
     *
     * @param impl The implementing service.
     * @param path The path of the service (must start with a '{@code /}' character).
     * @return The ServerEndpointConfig builder.
     */
    private static ServerEndpointConfig.Builder getConfigBuilder(Class<? extends WebSocketService> impl, String path) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("'path' must start with a '/' character");
        }

        return ServerEndpointConfig.Builder
            .create(impl, path)
            .subprotocols(Collections.singletonList("json"));
    }

    /**
     * Gets the configuration registered for this service instance.
     *
     * @return The service configuration.
     */
    @Override
    protected final WebSocketServiceConfiguration getConfiguration() {
        return (WebSocketServiceConfiguration)super.getConfiguration();
    }

    /**
     * Creates a service configuration. This should be overridden by
     * subclasses to provide implementation details. Subclasses should
     * call {@code super.createConfiguration()}
     * to obtain the configuration object instead of creating their own.
     *
     * @return The service configuration.
     */
    @Override
    protected WebSocketServiceConfiguration createConfiguration() {
        WebSocketServiceConfiguration config = new WebSocketServiceConfiguration(super.createConfiguration());

        // Get the message handlers
        MessageHandlerManager handlers = config.getMessageHandlers();

        // Add the default "STATUS" message handler
        handlers.set(BuiltInMessageTypes.System.STATUS, request -> {
            // Safely process status - we don't want an error in service code to fail the status response
            Object processedStatus;
            try {
                processedStatus = addExtraStatusInfo(request);
            } catch (RuntimeException ex) {
                processedStatus = new SafeErrorDetails("Failed to process status", ex, ex.getStackTrace(), true);
            }
            ServiceStatus status = new ServiceStatus(processedStatus);

            return status;
        });

        // Add the default "CLOSE_ALL_SESSIONS" message handler
        handlers.set(BuiltInMessageTypes.System.CLOSE_ALL_SESSIONS, request -> {
            CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Service is being undeployed");
            closeAllSessions(closeReason);
            return null;
        });

        return config;
    }

    public static <S extends WebSocketService, C extends WebSocketClient> C getServiceProxyClient(Class<S> callerServiceImplementation, Class<C> targetClientImplementation, String targetServiceName) {
        return WebSocketService.getServiceProxyClient(
            callerServiceImplementation,
            targetClientImplementation,
            targetServiceName,
            DEFAULT_MAX_RETRIES,
            DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNITS);
    }

    public static <S extends WebSocketService, C extends WebSocketClient> C getServiceProxyClient(Class<S> callerServiceImplementation, Class<C> clientImplementation, String targetServiceName, int maxRetries, long timeout, TimeUnit timeoutUnits) {
        try {
            return WebSocketService
                .getServiceConfiguration(callerServiceImplementation)
                .getServiceProxies()
                .get(targetServiceName, clientImplementation)
                .getClientAsync(maxRetries)
                .get(timeout, timeoutUnits);
        } catch (Exception ex) {
            String clientTypeName = clientImplementation.getPackageName() + "." + clientImplementation.getSimpleName();
            throw new RuntimeException("Failed to get client of type '" + clientTypeName + "' for service '" + targetServiceName + "'", ex);
        }
    }

    /**
     * Provides extra information about the status of the endpoint given some
     * data from the message body of the request.
     *
     * @param statusRequestBody The data from the status request message body.
     * @return The extra status information.
     */
    public Object addExtraStatusInfo(Object statusRequestBody) {
        // Basic JVM status will always be provided, so this method is not abstract since it shouldn't be mandatory to implement
        return null;
    }

    /**
     * Handles a response from a session.
     *
     * @param session The session.
     * @param response The response.
     */
    @Override
    protected final void handleResponse(Session session, Message response) {
        // Services shouldn't need to handle responses, so don't make it mandatory to override
        // If a service expects a response, it should make a new WebSocketClient instance to make its request
    }

    /**
     * Sends a message to all connected sessions.
     *
     * @param message The message to send.
     * @return The session IDs mapped to their respective task for sending the
     * message.
     */
    public final Map<String, CompletableFuture<Throwable>> broadcastMessage(Message message) {
        if (message == null) {
            throw new NullPointerException("'message' cannot be null");
        }

        // Send the message to all sessions
        Map<String, CompletableFuture<Throwable>> result = new HashMap<>();
        for (Session session : this.getSessions()) {
            CompletableFuture<Throwable> task = AsyncUtils.startTask(() -> {
                Throwable error = null;
                try {
                    WebSocketEndpoint.sendMessage(session, message);
                } catch (Throwable thr) {
                    error = thr;
                }

                return error;
            });
            String sessionId = session.getId();

            result.put(sessionId, task);
        }
        return result;
    }

    /**
     * Closes all client sessions.
     *
     * @param closeReason The reason to give to clients for closing the
     * session.
     */
    public final void closeAllSessions(CloseReason closeReason) {
        // Notify clients that this service is "going away"
        this.getSessions()
            .parallelStream()
            .forEach(clientSession -> {
                try {
                    clientSession.close(closeReason);
                } catch (IOException ex) {
                    System.err.println("Failed to notify client session that this service '" + this.getClass().getSimpleName() + "' is being undeployed: " + ex);
                    // TODO: log
                }
            });
    }

    @Override
    public final void onOpen(Session session, EndpointConfig config) {
        super.onOpen(session, config);

        // Create a new set to manage sessions if one doesn't already exist for this server implementation
        Set<Session> serverSessions = WebSocketService.sessions.computeIfAbsent(this.getClass(), (impl) -> Collections.newSetFromMap(new ConcurrentHashMap<>()));

        // Add the new session to the list of managed sessions
        serverSessions.add(session);
    }

    @SuppressWarnings("SynchronizationOnGetClass")
    @Override
    public final void onClose(Session session, CloseReason closeReason) {
        super.onClose(session, closeReason);

        // Remove the session from the list of managed sessions
        Set<Session> serverSessions = WebSocketService.sessions.get(this.getClass());
        serverSessions.remove(session);

        // TODO: investigate whether this is needed, and how to implement synchronization if it is
        synchronized (this.getClass()) {
            // If this was the last session for this implementation, remove the class reference to prevent memory leaks
            if (serverSessions.isEmpty()) {
                WebSocketService.sessions.remove(this.getClass());
            }
        }
    }

    /**
     * Gets the sessions for this service implementation.
     *
     * @return The sessions for this service implementation.
     */
    private Set<Session> getSessions() {
        return WebSocketService.sessions.get(this.getClass());
    }

    /**
     * Attempts to keep the session alive.
     */
    private static void keepAlive() {
        WebSocketService.sessions.values()
            .parallelStream()
            .flatMap(Set::stream)
            .forEach((session) -> ExceptionUtils.swallowRuntimeExceptions(() -> WebSocketEndpoint.sendPing(session)));
    }

    /**
     * Starts the task which automatically updates this resource's status.
     */
    private static void startKeepAliveTask() {
        if (keepAliveTaskFuture != null && !keepAliveTaskFuture.isDone()) {
            keepAliveTaskFuture.cancel(true);
        }

        keepAliveTaskFuture = scheduledExecutor.scheduleWithFixedDelay(
            WebSocketService::keepAlive,
            WebSocketService.PING_FREQUENCY,
            WebSocketService.PING_FREQUENCY,
            WebSocketService.PING_FREQUENCY_TIME_UNIT
        );
    }

    /**
     * Stops the task which automatically updates this resource's status.
     */
    @SuppressWarnings("unused")
    private static void stopKeepAliveTask() {
        if (keepAliveTaskFuture != null) {
            keepAliveTaskFuture.cancel(true);
        }
    }
}
