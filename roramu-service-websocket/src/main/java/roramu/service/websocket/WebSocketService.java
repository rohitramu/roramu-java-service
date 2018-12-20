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
 * The base type representing a service.
 */
public abstract class WebSocketService extends WebSocketEndpoint {
    // Keep-alive task
    private static final long PING_FREQUENCY = 30;
    private static final TimeUnit PING_FREQUENCY_TIME_UNIT = TimeUnit.SECONDS;
    private static final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread result = new Thread(runnable);
        result.setDaemon(true);
        result.setName("WebSocket keep-alive task");
        result.setUncaughtExceptionHandler((thread, thr) -> System.err.println("WebSocket keep-alive task threw exception: " + thr.toString()));

        return result;
    });
    private static ScheduledFuture<?> keepAliveTaskFuture = null;
    // Sessions
    private static final Map<Class<? extends WebSocketService>, Set<Session>> sessions = new ConcurrentHashMap<>();

    static {
        WebSocketService.startKeepAliveTask();
    }

    /**
     * Handler for returning status.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final MessageHandler statusHandler = TypedMessageHandler.create(
        MessageTypes.System.STATUS,
        request -> {
            // Safely process status - we don't want an error in service code to fail the status response
            Object processedStatus;
            try {
                processedStatus = processStatusRequest(request);
            } catch (RuntimeException ex) {
                processedStatus = new SafeErrorDetails("Failed to process status", ex, ex.getStackTrace(), true);
            }
            ServiceStatus status = new ServiceStatus(processedStatus);

            return status;
        }
    );

    /**
     * Handler for closing all sessions.
     */
    private final MessageHandler closeSessionsHandler = TypedMessageHandler.create(
        MessageTypes.System.CLOSE_ALL_SESSIONS,
        request -> {
            CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Service is being undeployed");
            closeAllSessions(closeReason);
            return null;
        }
    );

    /**
     * Gets the default {@link ServerEndpointConfig} object used for deploying a
     * service.
     *
     * @param impl The implementing service.
     * @param path The path of the service (must start with a '{@code /}'
     * character).
     *
     * @return The configuration.
     */
    public static final ServerEndpointConfig getDefaultConfig(Class<? extends WebSocketService> impl, String path) {
        return getConfigBuilder(impl, path).build();
    }

    /**
     * Gets the default {@link ServerEndpointConfig} object used for deploying a
     * service.
     *
     * @param impl The implementing service.
     * @param path The path of the service (must start with a '{@code /}'
     * character).
     * @param configurator The configurator implementation to be used in the
     * configuration. A configurator is most commonly used to intercept the
     * WebSocket handshake.
     *
     * @return The configuration.
     */
    public static final ServerEndpointConfig getDefaultConfig(Class<? extends WebSocketService> impl, String path, ServerEndpointConfig.Configurator configurator) {
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
     * @return The ServerEndpointConfig builder.
     */
    private static ServerEndpointConfig.Builder getConfigBuilder(Class<? extends WebSocketService> impl, String path) {
        return ServerEndpointConfig.Builder
            .create(impl, path)
            .subprotocols(Collections.singletonList("json"));
    }

    @Override
    protected Map<String, MessageHandler> createMessageHandlers() {
        Map<String, MessageHandler> handlers = super.createMessageHandlers();

        // Add the default "STATUS" message handler
        handlers.put(MessageTypes.System.STATUS.getName(), statusHandler);

        // Add the default "CLOSE_ALL_SESSIONS" message handler
        handlers.put(MessageTypes.System.CLOSE_ALL_SESSIONS.getName(), closeSessionsHandler);

        return handlers;
    }

    /**
     * Provides extra information about the status of the endpoint given some
     * request data.
     *
     * @param requestData The request data.
     *
     * @return The extra status information.
     */
    public Object processStatusRequest(Object requestData) {
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
     *
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
            WebSocketService.PING_FREQUENCY_TIME_UNIT);
    }

    /**
     * Stops the task which automatically updates this resource's status.
     */
    private static void stopKeepAliveTask() {
        if (keepAliveTaskFuture != null) {
            keepAliveTaskFuture.cancel(true);
        }
    }
}
