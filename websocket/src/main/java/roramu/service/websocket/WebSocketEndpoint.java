package roramu.service.websocket;

import org.apache.commons.io.IOUtils;
import roramu.util.exception.ExceptionUtils;
import roramu.util.json.JsonUtils;
import roramu.util.json.RawJsonString;
import roramu.util.reflection.TypeInfo;
import roramu.util.string.StringUtils;
import roramu.util.time.TimeUtils;

import javax.websocket.CloseReason;
import javax.websocket.DecodeException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.SessionException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Defines a WebSocket endpoint which can handle multiple types of messages.
 * <p>
 * NOTE: Every subclass that defines a constructor MUST ALSO define a
 * default constructor. This allows instances of this class to be created
 * during deployment. The default constructor may be private.
 */
public abstract class WebSocketEndpoint extends Endpoint {
    /**
     * The maximum size of a message (in characters) that can be sent as text.
     * Messages over this size limit will have to be sent as a byte stream.
     */
    private static final long MAX_TEXT_MESSAGE_LENGTH = 65536; // 64kB is the max WebSocket text message size

    /**
     * Endpoint configuration per subclass.
     */
    private static final Map<Class<? extends WebSocketEndpoint>, WebSocketEndpointConfiguration> endpointConfigurations = new ConcurrentHashMap<>();

    /**
     * Constructs a new WebSocket endpoint and populates the endpoint configuration.
     */
    public WebSocketEndpoint() {
        Class<? extends WebSocketEndpoint> endpointImplementation = this.getClass();

        // Set the endpoint configuration for each subtype
        if (WebSocketEndpoint.endpointConfigurations.get(endpointImplementation) == null) {
            WebSocketEndpoint.endpointConfigurations.put(endpointImplementation, this.createConfiguration());
        }
    }

    /**
     * Gets the configuration for a given endpoint implementation.
     *
     * @param endpointImplementation The endpoint implementation.
     * @return The configuration for the endpoint implementation if one exists, otherwise null.
     */
    public static final WebSocketEndpointConfiguration getEndpointConfiguration(Class<? extends WebSocketEndpoint> endpointImplementation) {
        if (endpointImplementation == null) {
            throw new IllegalArgumentException("'endpointImplementation' cannot be null");
        }

        return WebSocketEndpoint.endpointConfigurations.get(endpointImplementation);
    }

    /**
     * Sends a ping.
     *
     * @param session The session to use when sending the ping.
     */
    public static final void sendPing(Session session) {
        try {
            long pingMessage = Instant.now().toEpochMilli();
            session.getBasicRemote().sendPing(ByteBuffer.wrap(Long.toString(pingMessage).getBytes()));
        } catch (IOException | IllegalArgumentException ex) {
            throw new RuntimeException("Failed to send ping", ex);
        }
    }

    /**
     * Gets the configuration for this WebSocket endpoint object.
     *
     * @return The WebSocket endpoint's configuration.
     */
    protected WebSocketEndpointConfiguration getConfiguration() {
        return endpointConfigurations.get(this.getClass());
    }

    /**
     * Creates the configuration for this WebSocket endpoint object.
     * <p>
     * NOTE: Implementations should first call
     * {@code super.createConfiguration()} to get the default configuration
     * (e.g. the "STATUS" and "ERROR" message handlers).
     * </p>
     * <p>
     *
     * @return The message handlers. This method should never return null.
     */
    protected WebSocketEndpointConfiguration createConfiguration() {
        return new WebSocketEndpointConfiguration();
    }

    /**
     * Handles responses to requests sent by this endpoint.
     *
     * @param session The session.
     * @param response The response message.
     */
    protected abstract void handleResponse(Session session, Message response);

    @SuppressWarnings("Convert2Lambda")
    private javax.websocket.MessageHandler.Whole<String> getSessionStringMessageHandler(Session session) {
        return new javax.websocket.MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
                handleMessage(session, message);
            }
        };
    }

    @SuppressWarnings("Convert2Lambda")
    private javax.websocket.MessageHandler.Whole<InputStream> getSessionInputStreamMessageHandler(Session session) {
        return new javax.websocket.MessageHandler.Whole<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
                String stringMessage = null;
                try (BufferedInputStream is = new BufferedInputStream(message)) {
                    stringMessage = IOUtils.toString(is, Charset.forName("UTF-8"));
                } catch (IOException ex) {
                    // Swallow exception to make sure we keep the connection open
                    ex.printStackTrace();
                    // TODO: log
                }

                if (stringMessage != null) {
                    handleMessage(session, stringMessage);
                }
            }
        };
    }

    private void handleMessage(Session session, String stringMessage) {
        Message message = null;
        long startProcessingMillis = TimeUtils.getCurrentMillis();
        try {
            message = JsonUtils.read(stringMessage, new TypeInfo<Message>() {});

            if (message.getType() == null) {
                throw new NullPointerException("Message type is null");
            }
            String messageType = message.getType();
            message.setReceivedMillis(TimeUtils.getCurrentMillis());

            if (message.isResponse()) {
                handleResponse(session, message);
            } else {
                // Make sure a handler exists for this message type
                MessageHandler messageHandler = this.getConfiguration().getMessageHandlers().get(messageType);
                if (messageHandler == null) {
                    throw new IllegalArgumentException("Unknown message type '" + messageType + "'");
                }

                // Process the message
                RawJsonString response = messageHandler.handleMessage(message.getBody());

                // If the client is expecting a response, send one back
                if (message.isExpectingResponse()) {
                    // Create a message from the response string
                    Message responseMessage = Message.createSuccessResponse(message, response);

                    // Set the processing times
                    responseMessage.setStartProcessingMillis(startProcessingMillis);
                    responseMessage.setStopProcessingMillis(TimeUtils.getCurrentMillis());

                    // Send response synchronously - we want to know if we failed to send the response
                    if (session.isOpen()) {
                        WebSocketEndpoint.sendMessage(session, responseMessage);
                    }
                }
            }
        } catch (Throwable ex) {
            // We don't ever want to let the endpoint crash, so catch Throwable
            // TODO: log
            final Message request = message;
            ExceptionUtils.swallowRuntimeExceptions(() -> {
                Message errorMessage = Message.createErrorResponse(request, ex);

                // Set the processing times
                errorMessage.setStartProcessingMillis(startProcessingMillis);
                errorMessage.setStopProcessingMillis(TimeUtils.getCurrentMillis());

                WebSocketEndpoint.sendMessage(session, errorMessage);
            });
        }
    }

    /**
     * Sends a message. Messages that are {@value MAX_TEXT_MESSAGE_LENGTH} bytes
     * (characters) or less will be sent as text. Larger messages will be sent
     * as a byte stream.
     *
     * @param session The WebSocket session to use when sending the message.
     * @param message The message.
     */
    protected static void sendMessage(Session session, Message message) {
        if (message == null) {
            throw new NullPointerException("'message' cannot be null");
        }
        if (!session.isOpen()) {
            throw new IllegalStateException("Cannot send message - WebSocket session is not open.");
        }

        // Set the "sentMillis" property if the message is not a response
        if (!message.isResponse()) {
            message.setSentMillis(TimeUtils.getCurrentMillis());
        }

        // Serialize the message
        String jsonMessage = WebSocketEndpoint.serializeMessage(message);

        // Send the message
        try {
            if (jsonMessage.length() <= MAX_TEXT_MESSAGE_LENGTH) {
                // If the length of the message is small, send the string message as-is
                session.getAsyncRemote().sendText(jsonMessage).get();
            } else {
                // If the message is large, send it as a stream
                try (BufferedOutputStream os = new BufferedOutputStream(session.getBasicRemote().getSendStream())) {
                    System.out.println("Sending large message as bytes. Text message size: " + jsonMessage.length() + ".");
                    byte[] byteMessage = jsonMessage.getBytes(Charset.forName("UTF-8"));
                    IOUtils.write(byteMessage, os);
                    os.flush();
                }
            }
        } catch (IOException | ExecutionException | InterruptedException ex) {
            throw new RuntimeException("Failed to send message asynchronously", ex);
        }
    }

    /**
     * Serializes a message into a JSON string.
     *
     * @param message The message to serialize.
     * @return The JSON string representing the message.
     */
    private static String serializeMessage(Message message) {
        if (message == null) {
            throw new NullPointerException("'message' cannot be null");
        }

        // Convert the message to JSON
        String jsonMessage = JsonUtils.write(message);

        return jsonMessage;
    }

    /**
     * Logic to run before a session is opened (e.g. validation).
     *
     * @param session The session that will be opened.
     * @return The {@link CloseReason} if the session should be rejected,
     * otherwise null.
     */
    public CloseReason beforeOpen(Session session) {
        // This is to be overridden by implementations
        return null;
    }

    @Override
    @SuppressWarnings("Convert2Lambda")
    public void onOpen(Session session, EndpointConfig config) {
        CloseReason closeReason = this.beforeOpen(session);
        if (closeReason != null) {
            if (session.isOpen()) {
                try {
                    session.close(closeReason);
                } catch (IOException ex) {
                    throw new RuntimeException("Could not close rejected session", ex);
                }
            }
        } else {
            session.addMessageHandler(this.getSessionStringMessageHandler(session));
            session.addMessageHandler(this.getSessionInputStreamMessageHandler(session));
            session.addMessageHandler(new javax.websocket.MessageHandler.Whole<PongMessage>() {
                @Override
                public void onMessage(PongMessage message) {
                    long pingSendTime = Long.parseLong(new String(message.getApplicationData().array()));
                    @SuppressWarnings("unused")
                    long roundTripMillis = TimeUtils.getCurrentMillis() - pingSendTime;
                    // TODO: log
                }
            });
            System.out.println("Connected to session '" + session.getId() + "'");
        }
    }

    /**
     * Logic to run before a session is closed (e.g. cleanup tasks).
     *
     * @param session The session that will be closed.
     * @param closeReason The reason that the session is closing.
     */
    @SuppressWarnings("EmptyMethod")
    public void beforeClose(Session session, CloseReason closeReason) {
        // This is to be overridden by implementations
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        System.out.println(StringUtils.padRight(30, "[" + this.getClass().getSimpleName() + "]") + " Closing session '" + session.getId() + "' " + closeReason.toString());
        this.beforeClose(session, closeReason);
        //TODO: log
    }

    @Override
    public final void onError(Session session, Throwable thr) {
        if (session != null) {
            System.err.println("Error for session '" + session.getId() + "': " + thr.toString());
        }

        // Handle different kinds of errors
        String errorMessage;
        if (thr instanceof SessionException) {
            // Connection problem
            //TODO: handle connection problem in WebSocket endpoint
            //TODO: log

            errorMessage = "Problem with WebSocket session";
        } else if (thr instanceof DecodeException) {
            // Conversion error before message handler was called
            //TODO: handle conversion error in WebSocket endpoint
            //TODO: log

            errorMessage = "Problem with decoding message";
        } else {
            // Exception thrown in the session's thread
            //TODO: handle uncaught exception in session
            errorMessage = "Uncaught exception in session";
        }
        System.err.println(errorMessage);

        // If the session is still open, send an error message to the client
        if (session != null && session.isOpen()) {
            // Create the error message
            Message message = Message.createErrorResponse(thr);

            // Send the message to the client
            WebSocketEndpoint.sendMessage(session, message);
        }
    }
}
