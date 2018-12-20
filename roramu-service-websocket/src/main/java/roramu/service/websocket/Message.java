package roramu.service.websocket;

import com.fasterxml.jackson.annotation.JsonRawValue;
import roramu.util.json.JsonUtils;

import java.util.UUID;

/**
 * Represents a message which can be sent between services.
 *
 * TODO: javadoc comments for property getters and setters
 */
public final class Message {
    private String requestId;
    private String messageType;
    private String body;
    private Long sentMillis;
    private Long receivedMillis;
    private Long startProcessingMillis;
    private Long stopProcessingMillis;

    /**
     * Private default constructor to stop callers from creating an instance
     * directly, and also to allow a JSON string to be deserialized into a
     * Message.
     */
    private Message() {}

    /**
     * Creates a message that can be sent between services.
     *
     * @param requestId The request ID to be used for synchronous calls. If
     * this is null, this message should be sent asynchronously without waiting
     * for a response.
     * @param messageType The type of message being sent.
     * @param jsonBody The JSON message body.
     */
    private Message(String requestId, String messageType, String jsonBody) {
        this.requestId = requestId;
        this.messageType = messageType;
        this.body = jsonBody;
    }

    /**
     * Creates a message that can be sent between services.
     *
     * @param isExpectingResponse Whether or not the client should request an
     * acknowledgment from the service it calls.
     * @param messageType The type of message being sent.
     * @param jsonBody The JSON message body.
     *
     * @return The new message.
     */
    public static final Message create(boolean isExpectingResponse, String messageType, String jsonBody) {
        if (MessageTypes.isError(messageType)) {
            throw new IllegalArgumentException("Use the 'createErrorResponse' method when creating an error response");
        }
        if (MessageTypes.isResponse(messageType)) {
            throw new IllegalArgumentException("Use the 'createResponse' method when creating a successfulResponse");
        }

        String requestId = null;
        if (isExpectingResponse) {
            requestId = UUID.randomUUID().toString();
        }

        return new Message(requestId, messageType, jsonBody);
    }

    /**
     * Creates a message that can be sent as a successful response to a previous
     * message.
     *
     * @param request The request message to create a response to.
     * @param jsonResponse The JSON body of the response.
     *
     * @return The response to the given request message.
     */
    public static final Message createSuccessResponse(Message request, String jsonResponse) {
        if (request == null) {
            throw new NullPointerException("'request' cannot be null");
        }

        // Make sure that the request is expecting a response
        if (!request.isExpectingResponse()) {
            throw new IllegalArgumentException("The request message is not expecting a response.");
        }

        Message responseMessage = new Message(request.getRequestId(), MessageTypes.Response.RESPONSE.getName(), jsonResponse);
        responseMessage.setSentMillis(request.getSentMillis());

        return responseMessage;
    }

    /**
     * Create a message that can be sent to indicate an error.
     *
     * @param error The error response.
     *
     * @return The error response message.
     */
    public static final Message createErrorResponse(Throwable error) {
        return Message.createErrorResponse(null, error);
    }

    /**
     * Creates a message that can be sent as an error response to a previous
     * message.
     *
     * @param request The request message to create an error response to.
     * @param error The error response.
     *
     * @return The error response to the given request message.
     */
    public static final Message createErrorResponse(Message request, Throwable error) {
        return Message.createErrorResponse(request, error, 0);
    }

    /**
     * Creates a message that can be sent as an error response to a previous
     * message.
     *
     * @param request The request message to create an error response to.
     * @param error The error response.
     * @param stackTraceDepth How many items from the stack trace to include in
     * the error response.
     *
     * @return The error response to the given request message.
     */
    public static final Message createErrorResponse(Message request, Throwable error, int stackTraceDepth) {
        String requestId = null;
        Long sentMillis = null;
        if (request != null) {
            requestId = request.getRequestId();
            sentMillis = request.getSentMillis();
        }

        // Serialize the error
        String messageType = MessageTypes.Response.ERROR.getName();
        Throwable cause = error.getCause();
        String causeMessage = null;
        if (cause != null) {
            causeMessage = cause.getMessage();
        }
        SafeErrorDetails exceptionDetails = new SafeErrorDetails(error.getMessage(), causeMessage, error.getStackTrace(), stackTraceDepth);
        String exceptionJson = JsonUtils.write(exceptionDetails);

        Message response = new Message(requestId, messageType, exceptionJson);
        response.setSentMillis(sentMillis);

        return response;
    }

    public final String getRequestId() {
        return requestId;
    }

    private void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public final String getMessageType() {
        return messageType;
    }

    private void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    @JsonRawValue
    public final String getBody() {
        return this.body;
    }

    private void setBody(Object body) {
        // It's ok to use JsonUtils directly here since it will not be exposed
        this.body = JsonUtils.write(body);
    }

    public final Long getSentMillis() {
        return this.sentMillis;
    }

    protected final void setSentMillis(Long sentMillis) {
        this.sentMillis = sentMillis;
    }

    public final Long getReceivedMillis() {
        return this.receivedMillis;
    }

    protected final void setReceivedMillis(Long receivedMillis) {
        this.receivedMillis = receivedMillis;
    }

    public final Long getStartProcessingMillis() {
        return startProcessingMillis;
    }

    public final void setStartProcessingMillis(Long startProcessingMillis) {
        this.startProcessingMillis = startProcessingMillis;
    }

    public final Long getStopProcessingMillis() {
        return stopProcessingMillis;
    }

    public final void setStopProcessingMillis(Long stopProcessingMillis) {
        this.stopProcessingMillis = stopProcessingMillis;
    }

    /**
     * Checks whether or not this message is a response to a previous request.
     *
     * @return True if this message is a response, otherwise false.
     */
    public final boolean isResponse() {
        return this.requestId != null && MessageTypes.isResponse(this.messageType);
    }

    /**
     * Checks whether this message conveys an error message.
     *
     * @return True if the message represents an error, otherwise false.
     */
    public final boolean isError() {
        return MessageTypes.isError(this.messageType);
    }

    /**
     * Whether or not the client that sent this message is expecting a
     * response.
     *
     * @return True if the message requires a response, otherwise false.
     */
    public final boolean isExpectingResponse() {
        return this.requestId != null && !this.isResponse();
    }
}
