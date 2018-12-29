package roramu.service.websocket;

import roramu.util.async.AsyncUtils;
import roramu.util.json.JsonConverter;
import roramu.util.json.JsonUtils;
import roramu.util.json.RawJsonString;
import roramu.util.string.StringUtils;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A WebSocket client.
 *
 * Service implementations should extend this class to provide implementation-specific methods for interacting with the
 * service's APIs.
 */
public abstract class WebSocketClient extends WebSocketEndpoint implements AutoCloseable {
    // TODO: Connect to dependencies when locations are updated
    /**
     * Tracks the requests that are awaiting a response. It is a mapping of
     * request IDs to CallInfo objects for each session.
     */
    private static final ConcurrentHashMap<Session, Map<String, WebSocketClient.CallInfo>> waitingRequests = new ConcurrentHashMap<>();
    /**
     * The Session object representing this instance's currently open
     * connection.
     */
    private Session session;

    /**
     * Handler for errors.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final MessageHandler errorHandler = TypedMessageHandler.create(
        MessageTypes.Response.ERROR,
        (request) -> {
            System.err.println("ERROR:\n" + JsonUtils.write(request));
            return null;
        }
    );

    /**
     * Gets the default {@link ClientEndpointConfig} object used for creating a
     * client.
     *
     * @return The configuration.
     */
    public static final ClientEndpointConfig getDefaultConfig() {
        return getConfigBuilder().build();
    }

    /**
     * Gets the default {@link ClientEndpointConfig} object used for creating a
     * client.
     *
     * @param configurator The configurator implementation to be used in the
     * configuration. A configurator is most commonly used to intercept the
     * WebSocket handshake.
     *
     * @return The configuration.
     */
    public static final ClientEndpointConfig getDefaultConfig(ClientEndpointConfig.Configurator configurator) {
        if (configurator == null) {
            throw new NullPointerException("'configurator' cannot be null");
        }

        return getConfigBuilder()
            .configurator(configurator)
            .build();
    }

    /**
     * Gets the builder for a {@link ClientEndpointConfig} with the default
     * options set.
     *
     * @return The ClientEndpointConfig builder.
     */
    private static ClientEndpointConfig.Builder getConfigBuilder() {
        return ClientEndpointConfig.Builder
            .create()
            .preferredSubprotocols(Collections.singletonList("json"));
    }

    /**
     * Gets the {@link roramu.service.websocket.WebSocketClient.CallInfo}
     * object for a request.
     *
     * @param session The session in which the request was sent.
     * @param requestId The request ID.
     *
     * @return The CallInfo object if it exists, otherwise null.
     */
    private static CallInfo getCallInfoForRequest(Session session, String requestId) {
        Map<String, CallInfo> callInfoMap = WebSocketClient.waitingRequests.get(session);
        if (callInfoMap == null) {
            throw new NullPointerException("Cannot get CallInfo - this session is not registered");
        }

        return callInfoMap.get(requestId);
    }

    /**
     * Sets the CallInfo object for a request if it isn't already set.
     *
     * @param requestId The request ID of the request.
     * @param callInfo The CallInfo.
     *
     * @return True if the request is now associated with the given CallInfo,
     * otherwise false (if a CallInfo already existed for this request).
     */
    private static boolean setCallInfoForRequestIfAbsent(Session session, String requestId, CallInfo callInfo) {
        Map<String, CallInfo> callInfoMap = WebSocketClient.waitingRequests.get(session);
        if (callInfoMap == null) {
            throw new NullPointerException("Cannot set CallInfo - this session is not registered");
        }

        CallInfo oldRequest = callInfoMap.putIfAbsent(requestId, callInfo);
        return oldRequest == null;
    }

    /**
     * Registers a message's request ID if it is expecting a response.
     *
     * @param message The message.
     */
    private static CallInfo startTrackingRequest(Session session, Message message) {
        // If we're expecting a result, put it on the waiting queue
        if (!message.isExpectingResponse()) {
            throw new IllegalArgumentException("Cannot track a message which is not expecting a response");
        }

        // Create the CallInfo object which will track the response
        String requestId = message.getRequestId();
        CallInfo callInfo = new CallInfo(message);

        // Register the CallInfo object so the message listener thread can notify this thread that a response has arrived
        if (!WebSocketClient.setCallInfoForRequestIfAbsent(session, requestId, callInfo)) {
            throw new IllegalArgumentException("Request with requestId '" + requestId + "' is already waiting for a response");
        }

        return callInfo;
    }

    /**
     * Unregisters a message's request ID.
     *
     * @param requestId The message's request ID.
     *
     * @return The old CallInfo object which was used to track the request.
     */
    private static CallInfo stopTrackingRequest(Session session, String requestId) {
        Map<String, CallInfo> callInfoMap = WebSocketClient.waitingRequests.get(session);
        if (callInfoMap == null) {
            throw new NullPointerException("Cannot stop tracking request - this session is not registered");
        }

        return callInfoMap.remove(requestId);
    }

    /**
     * Creates a WebSocket instance to manage an existing open session.
     * <p>
     *     NOTE: The session will remain open indefinitely until either the
     *     connection times out, the session is replaced with another session,
     *     or {@link #close()} is called.
     * </p>
     *
     * @param session The session.
     */
    public final void setSession(Session session) {
        if (session == null) {
            throw new NullPointerException("Session cannot be null");
        }
        if (!session.isOpen()) {
            throw new IllegalArgumentException("Session must be open to use it with a client");
        }

        // Replace the old session with the new session
        this.session = session;
        WebSocketClient.waitingRequests.putIfAbsent(session, new ConcurrentHashMap<>());
    }

    /**
     * Closes the session which is used by this WebSocketClient instance.
     *
     * @throws IllegalStateException If this client is not currently managing a
     * session. Set the session using {@link #setSession}.
     * @throws UncheckedIOException If there was an error while closing the
     * session.
     */
    @Override
    public final void close() throws IllegalStateException, UncheckedIOException {
        this.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Client '" + this.getClass().getSimpleName() + "' is closing session"));
    }

    /**
     * Closes the session which is used by this WebSocketClient instance.
     *
     * @param closeReason The close reason which will be given to the server.
     *
     * @throws IllegalStateException If this client is not currently managing a
     * session. Set the session using {@link #setSession(Session)}.
     * @throws UncheckedIOException If there was an error while closing the
     * session.
     */
    public final void close(CloseReason closeReason) throws IllegalStateException, UncheckedIOException {
        if (this.session == null) {
            throw new IllegalStateException("Session is not set for this client. Set the session using 'WebSocketClient.setSession()' before attempting to close it.");
        }
        if (closeReason == null) {
            throw new NullPointerException("'closeReason' cannot be null");
        }

        // Close the session if it is currently open
        if (this.session.isOpen()) {
            try {
                this.session.close(closeReason);
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to close session", ex);
            }
        }

        // Remove the waiting requests
        WebSocketClient.waitingRequests.remove(this.session);
    }

    @Override
    protected final Map<String, MessageHandler> createMessageHandlers() {
        Map<String, MessageHandler> handlers = super.createMessageHandlers();

        // Add the "ERROR" message handler
        handlers.putIfAbsent(MessageTypes.Response.ERROR.getName(), errorHandler);

        return handlers;
    }

    @Override
    public final void onOpen(Session session, EndpointConfig config) {
        super.onOpen(session, config);
    }

    @Override
    public final void onClose(Session session, CloseReason closeReason) {
        super.onClose(session, closeReason);
        WebSocketClient.waitingRequests.remove(session);
    }

    /**
     * Checks whether the session is open or not.
     *
     * @return True if the session is open, otherwise false.
     */
    public final boolean isOpen() {
        return this.session != null && this.session.isOpen();
    }

    /**
     * Handles a response message from a given WebSocket session.
     *
     * @param session The WebSocket session.
     * @param response The response message.
     */
    @Override
    protected final void handleResponse(Session session, Message response) {
        String requestId = response.getRequestId();
        CallInfo callInfo = getCallInfoForRequest(session, requestId);
        if (callInfo != null) {
            // We were waiting for this response, so handle it
            callInfo.setResult(response);
            long roundtripTime = response.getReceivedMillis() - response.getSentMillis();
            long processingTime = response.getStopProcessingMillis() - response.getStartProcessingMillis();

            String message =
                "[" + requestId + "] "
                    + StringUtils.padRight(30, callInfo.request.getMessageType())
                    + StringUtils.padRight(30, "Roundtrip time = " + roundtripTime + " ms")
                    + StringUtils.padRight(30, "Processing time = " + processingTime + " ms")
                    + "Network latency = " + (roundtripTime - processingTime) + " ms";

            // TODO: log
            System.out.println(message);
        } else {
            // Ignore the message if we weren't waiting on this response

            // TODO: log
            System.out.println("Ignored response with ID: " + response.getRequestId());
        }
    }

    /**
     * Blocks until a response to a particular request has been received. This
     * can only be called once per message that was sent with the {@link
     * #sendMessage(MessageType, Object)}  } method.
     *
     * @param <Res> The response type.
     * @param requestId The request ID of the request.
     * @param responseConverter The JsonConverter to use when deserializing the
     * response.
     *
     * @return The response to the request, which is extracted from the response
     * message.
     */
    private <Res> Response<Res> awaitMessageResponse(String requestId, JsonConverter<Res> responseConverter) {
        return awaitMessageResponse(requestId, responseConverter, 0, null);
    }

    /**
     * Blocks until a response to a particular request has been received. This
     * can only be called once per message that was sent with the
     * {@link #sendMessage(MessageType, Object)}  } method.
     *
     * @param <Res> The response type.
     * @param requestId The request ID of the request.
     * @param responseConverter The JsonConverter to use when deserializing the
     * response.
     * @param timeout How long to wait before giving up on the call and throwing
     * an exception. Set this to zero to disable the timeout.
     * @param timeoutUnits The units for the timeout.
     *
     * @return The response to the request, which is extracted from the response
     * message.
     */
    private <Res> Response<Res> awaitMessageResponse(String requestId, JsonConverter<Res> responseConverter, long timeout, TimeUnit timeoutUnits) {
        if (requestId == null) {
            throw new NullPointerException("'requestId' cannot be null");
        }
        if (responseConverter == null) {
            throw new NullPointerException("'responseConverter' cannot be null");
        }

        // Wait for the response
        CallInfo requestInfo = WebSocketClient.getCallInfoForRequest(this.session, requestId);
        if (requestInfo == null) {
            throw new IllegalArgumentException("Request ID '" + requestId + "' either does not have a response pending, or it was already retrieved");
        }

        // Get the response message
        Message responseMessage;
        try {
            if (timeout == 0) {
                responseMessage = requestInfo.awaitResult();
            } else {
                responseMessage = requestInfo.awaitResult(timeout, timeoutUnits);
            }
        } catch (TimeoutException ex) {
            responseMessage = Message.createErrorResponse(requestInfo.getRequestMessage(), ex);
        } finally {
            // Even if an exception is thrown (most likely due to timeout), stop tracking the request since we got the response
            WebSocketClient.stopTrackingRequest(this.session, requestId);
        }

        // Wrap the message in a Response object so the client can handle errors
        Response<Res> result = new Response<>(responseMessage, responseConverter);

        return result;
    }

    /**
     * Sends a message synchronously.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param messageBody The message body.
     */
    protected final <Req, Res> void sendMessage(MessageType<Req, Res> messageType, Req messageBody) {
        RawJsonString jsonBody = messageType.getRequestJsonConverter().serialize(messageBody);
        this.sendMessage(messageType.getName(), jsonBody);
    }

    /**
     * Sends a message synchronously.
     * <p>
     *     It is not recommended to use this overload unless the appropriate
     *     {@link MessageType} is not available. Instead, use the
     *     {@link #sendMessage(MessageType, Object)} method when possible.
     * </p>
     *
     * @param messageType The message type.
     * @param messageBody The JSON message body.
     */
    protected final void sendMessage(String messageType, RawJsonString messageBody) {
        Message message = Message.create(false, messageType, messageBody);

        // Send the message
        WebSocketEndpoint.sendMessage(this.session, message);
    }

    /**
     * Sends a message asynchronously without a message body. Use the
     * {@link #sendRequestAsync(MessageType, long, TimeUnit)} method to
     * set a timeout.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param messageType The message type.
     *
     * @return The response.
     */
    protected final <Req, Res> CompletableFuture<Response<Res>> sendRequestAsync(MessageType<Req, Res> messageType) {
        return this.sendRequestAsync(messageType, null);
    }

    /**
     * Sends a message asynchronously. Use the
     * {@link #sendRequestAsync(MessageType, Object, long, TimeUnit)} method to
     * set a timeout.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param messageBody The message body.
     *
     * @return The response.
     */
    protected final <Req, Res> CompletableFuture<Response<Res>> sendRequestAsync(MessageType<Req, Res> messageType, Req messageBody) {
        return this.sendRequestAsync(messageType, messageBody, 0, null);
    }

    /**
     * Sends a message asynchronously without a message body.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param timeout How long to wait before giving up on the call and throwing
     * an exception. Set this to zero to disable the timeout.
     * @param timeoutUnits The units for the timeout.
     *
     * @return The response.
     */
    protected final <Req, Res> CompletableFuture<Response<Res>> sendRequestAsync(MessageType<Req, Res> messageType, long timeout, TimeUnit timeoutUnits) {
        return this.sendRequestAsync(messageType, null, timeout, timeoutUnits);
    }

    /**
     * Sends a message asynchronously.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param messageBody The JSON message body.
     * @param timeout How long to wait before giving up on the call and throwing
     * an exception. Set this to zero to disable the timeout.
     * @param timeoutUnits The units for the timeout.
     *
     * @return The response.
     */
    protected final <Req, Res> CompletableFuture<Response<Res>> sendRequestAsync(MessageType<Req, Res> messageType, Req messageBody, long timeout, TimeUnit timeoutUnits) {
        RawJsonString jsonBody = messageType.getRequestJsonConverter().serialize(messageBody);
        return this.sendRequestAsync(messageType.getName(), jsonBody, messageType.getResponseJsonConverter(), timeout, timeoutUnits);
    }

    /**
     * Sends a message asynchronously without a message body. Use the
     * {@link #sendRequestAsync(String, JsonConverter, long, TimeUnit)}
     * method to set a timeout.
     * <p>
     *     NOTE: It is not recommended to use this overload unless the appropriate
     *     {@link MessageType} is not available. Use the
     *     {@link #sendRequestAsync(MessageType, Object)} method when possible.
     * </p>
     *
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param responseConverter The JsonConverter to use when deserializing the
     * response.
     *
     * @return The deserialized response.
     */
    protected final <Res> CompletableFuture<Response<Res>> sendRequestAsync(String messageType, JsonConverter<Res> responseConverter) {
        return this.sendRequestAsync(messageType, null, responseConverter, 0, null);
    }

    /**
     * Sends a message asynchronously. Use the
     * {@link #sendRequestAsync(String, RawJsonString, JsonConverter, long, TimeUnit)}
     * method to set a timeout.
     * <p>
     *     NOTE: It is not recommended to use this overload unless the appropriate
     *     {@link MessageType} is not available. Use the
     *     {@link #sendRequestAsync(MessageType, Object)} method when possible.
     * </p>
     *
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param requestBody The JSON message body.
     * @param responseConverter The JsonConverter to use when deserializing the
     * response.
     *
     * @return The deserialized response.
     */
    protected final <Res> CompletableFuture<Response<Res>> sendRequestAsync(String messageType, RawJsonString requestBody, JsonConverter<Res> responseConverter) {
        return this.sendRequestAsync(messageType, requestBody, responseConverter, 0, null);
    }

    /**
     * Sends a message asynchronously without a message body.
     * <p>
     *     NOTE: It is not recommended to use this overload unless the
     *     appropriate {@link MessageType} is not available. Use the
     *     {@link #sendRequestAsync(MessageType)} method when possible.
     * </p>
     *
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param responseConverter The JsonConverter to use when deserializing the
     * response.
     * @param timeout How long to wait before giving up on the call and throwing
     * an exception. Set this to zero to disable the timeout.
     * @param timeoutUnits The units for the timeout.
     *
     * @return The deserialized response.
     */
    protected final <Res> CompletableFuture<Response<Res>> sendRequestAsync(String messageType, JsonConverter<Res> responseConverter, long timeout, TimeUnit timeoutUnits) {
        return this.sendRequestAsync(messageType, null, responseConverter, timeout, timeoutUnits);
    }

    /**
     * Sends a message asynchronously.
     * <p>
     *     NOTE: It is not recommended to use this overload unless the
     *     appropriate {@link MessageType} is not available. Use the
     *     {@link #sendRequestAsync(MessageType, Object)} method when possible.
     * </p>
     *
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param requestBody The JSON message body.
     * @param responseConverter The JsonConverter to use when deserializing the
     * response.
     * @param timeout How long to wait before giving up on the call and throwing
     * an exception. Set this to zero to disable the timeout.
     * @param timeoutUnits The units for the timeout.
     *
     * @return The deserialized response.
     */
    protected final <Res> CompletableFuture<Response<Res>> sendRequestAsync(String messageType, RawJsonString requestBody, JsonConverter<Res> responseConverter, long timeout, TimeUnit timeoutUnits) {
        if (messageType == null) {
            throw new NullPointerException("'messageType' cannot be null");
        }
        if (responseConverter == null) {
            throw new NullPointerException("'responseConverter' cannot be null");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("'timeout' cannot be negative");
        }
        if (timeoutUnits == null && timeout > 0) {
            throw new NullPointerException("'timeoutUnits' cannot be null if the given timeout is greater than 0");
        }

        CompletableFuture<Response<Res>> result = AsyncUtils.startTask(() -> this.sendRequest(messageType, requestBody, responseConverter, timeout, timeoutUnits));

        return result;
    }

    /**
     * Sends a request without a message body and waits for the response.
     * This method will block indefinitely until a response is received.
     * Use the {@link #sendRequest(MessageType, long, TimeUnit)} method
     * to set a timeout.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param messageType The message type.
     *
     * @return The response.
     */
    protected final <Req, Res> Response<Res> sendRequest(MessageType<Req, Res> messageType) {
        return this.sendRequest(messageType, null);
    }

    /**
     * Sends a request and waits for the response. This method will block
     * indefinitely until a response is received. Use the
     * {@link #sendRequest(MessageType, Object, long, TimeUnit)}
     * method to set a timeout.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param requestBody The message body.
     *
     * @return The response.
     */
    protected final <Req, Res> Response<Res> sendRequest(MessageType<Req, Res> messageType, Req requestBody) {
        RawJsonString jsonBody = messageType.getRequestJsonConverter().serialize(requestBody);
        return this.sendRequest(messageType.getName(), jsonBody, messageType.getResponseJsonConverter());
    }

    /**
     * Sends a request without a message body and waits for the response.
     * This method will block indefinitely until a response is received.
     * Use the {@link #sendRequest(String, JsonConverter, long, TimeUnit)}
     * method to set a timeout.
     * <p>
     *     NOTE: It is not recommended to use this overload unless the
     *     appropriate {@link MessageType} is not available. Use the
     *     {@link #sendRequest(MessageType)} method when possible.
     * </p>
     *
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param responseConverter The JsonConverter to use when deserializing the
     * response.
     *
     * @return The response.
     */
    protected final <Res> Response<Res> sendRequest(String messageType, JsonConverter<Res> responseConverter) {
        return this.sendRequest(messageType, null, responseConverter);
    }

    /**
     * Sends a request and waits for the response. This method will block
     * indefinitely until a response is received. Use the
     * {@link #sendRequest(String, RawJsonString, JsonConverter, long, TimeUnit)}
     * method to set a timeout.
     * <p>
     *     NOTE: It is not recommended to use this overload unless the
     *     appropriate {@link MessageType} is not available. Use the
     *     {@link #sendRequest(MessageType, Object)} method when possible.
     * </p>
     *
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param requestBody The JSON message body.
     * @param responseConverter The JsonConverter to use when deserializing the
     * response.
     *
     * @return The response.
     */
    protected final <Res> Response<Res> sendRequest(String messageType, RawJsonString requestBody, JsonConverter<Res> responseConverter) {
        return this.sendRequest(
            messageType,
            requestBody,
            responseConverter,
            0, null);
    }

    /**
     * Sends a request without a message body and waits for the response, using the
     * specified timeout period for awaiting the response.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param timeout The timeout.
     * @param timeoutUnits The timeout units.
     *
     * @return The response.
     */
    protected final <Req, Res> Response<Res> sendRequest(MessageType<Req, Res> messageType, long timeout, TimeUnit timeoutUnits) {
        return this.sendRequest(messageType, null, timeout, timeoutUnits);
    }

    /**
     * Sends a request and waits for the response, using the specified timeout
     * period for awaiting the response.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param requestBody The message body.
     * @param timeout The timeout.
     * @param timeoutUnits The timeout units.
     *
     * @return The response.
     */
    protected final <Req, Res> Response<Res> sendRequest(MessageType<Req, Res> messageType, Req requestBody, long timeout, TimeUnit timeoutUnits) {
        RawJsonString jsonBody = messageType.getRequestJsonConverter().serialize(requestBody);
        return this.sendRequest(messageType.getName(), jsonBody, messageType.getResponseJsonConverter(), timeout, timeoutUnits);
    }

    /**
     * Sends a request without a message body and waits for the response,
     * using the specified timeout period for awaiting the response.
     * <p>
     *     NOTE: It is not recommended to use this overload unless the
     *     appropriate {@link MessageType} is not available. Instead, use the
     *     {@link #sendRequest(MessageType, long, TimeUnit)}
     *     method when possible.
     * </p>
     *
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param responseConverter The JsonConverter to use when deserializing the
     * response.
     * @param timeout The timeout.
     * @param timeoutUnits The timeout units.
     *
     * @return The response.
     */
    protected final <Res> Response<Res> sendRequest(String messageType, JsonConverter<Res> responseConverter, long timeout, TimeUnit timeoutUnits) {
        return this.sendRequest(messageType, null, responseConverter, timeout, timeoutUnits);
    }

    /**
     * Sends a request and waits for the response, using the specified timeout
     * period for awaiting the response.
     * <p>
     *     NOTE: It is not recommended to use this overload unless the
     *     appropriate {@link MessageType} is not available. Instead, use the
     *     {@link #sendRequest(MessageType, Object, long, TimeUnit)}
     *     method when possible.
     * </p>
     *
     * @param <Res> The response type.
     * @param messageType The message type.
     * @param requestBody The JSON message body.
     * @param responseConverter The JsonConverter to use when deserializing the
     * response.
     * @param timeout The timeout.
     * @param timeoutUnits The timeout units.
     *
     * @return The response.
     */
    protected final <Res> Response<Res> sendRequest(String messageType, RawJsonString requestBody, JsonConverter<Res> responseConverter, long timeout, TimeUnit timeoutUnits) {
        if (messageType == null) {
            throw new NullPointerException("'messageType' cannot be null");
        }
        if (responseConverter == null) {
            throw new NullPointerException("'responseConverter' cannot be null");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("'timeout' cannot be negative");
        }
        if (timeoutUnits == null && timeout > 0) {
            throw new NullPointerException("'timeoutUnits' cannot be null if the given timeout is greater than 0");
        }

        // Create the message to be sent
        Message message = Message.create(true, messageType, requestBody);

        // Put the request on the waiting queue
        WebSocketClient.startTrackingRequest(this.session, message);

        // Make the call
        WebSocketEndpoint.sendMessage(this.session, message);

        // Wait for the response
        String requestId = message.getRequestId();
        Response<Res> response;
        if (timeout == 0) {
            response = awaitMessageResponse(requestId, responseConverter);
        } else {
            response = awaitMessageResponse(requestId, responseConverter, timeout, timeoutUnits);
        }

        return response;
    }

    /**
     * Represents a call which is waiting for a result.
     */
    private static final class CallInfo {
        private final Lock lock = new ReentrantLock();
        private final Condition receivedResultCondition = this.lock.newCondition();
        private final Message request;
        private volatile boolean receivedResult = false;
        private volatile Message result = null;

        public CallInfo(Message request) {
            if (request == null) {
                throw new NullPointerException("'request' cannot be null");
            }

            this.request = request;
        }

        public Message getRequestMessage() {
            return this.request;
        }

        private boolean hasResult() {
            return this.receivedResult;
        }

        private void setResult(Message result) {
            lock.lock();
            try {
                this.result = result;
                this.receivedResult = true;
                this.receivedResultCondition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        public Message awaitResult() {
            try {
                return awaitResult(0, null);
            } catch (TimeoutException ex) {
                // This should never happen
                throw new RuntimeException("Unexpected error which waiting for message response", ex);
            }
        }

        public Message awaitResult(long timeout, TimeUnit timeoutUnits) throws TimeoutException {
            if (timeout < 0) {
                throw new IllegalArgumentException("'timeout' cannot be negative");
            }
            if (timeoutUnits == null && timeout > 0) {
                throw new NullPointerException("'timeoutUnits' cannot be null if the given timeout is greater than 0");
            }

            lock.lock();
            try {
                boolean timedOut = false;
                boolean hasTimeout = timeout > 0;
                while (!this.receivedResult && !timedOut) {
                    try {
                        if (hasTimeout) {
                            timedOut = !this.receivedResultCondition.await(timeout, timeoutUnits);
                        } else {
                            this.receivedResultCondition.await();
                        }
                    } catch (InterruptedException ex) {
                        // Don't throw here, otherwise we can't keep waiting for the result
                        // TODO: log
                        //throw new RuntimeException("Call with request ID '" + this.requestId + "' was interrupted before receiving a response", ex);
                    }
                }

                if (!receivedResult && timedOut) {
                    throw new TimeoutException("Call with request ID '" + this.request.getRequestId() + "' timed out before receiving a response");
                }

                return this.result;
            } finally {
                lock.unlock();
            }
        }
    }
}
