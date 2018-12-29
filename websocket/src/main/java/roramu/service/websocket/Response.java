package roramu.service.websocket;

import roramu.util.json.JsonConverter;
import roramu.util.json.JsonUtils;
import roramu.util.json.RawJsonString;
import roramu.util.reflection.TypeInfo;

/**
 * Represents a response to a request.
 *
 * @param <T> The expected response type.
 */
public final class Response<T> {
    private final Message message;
    private final JsonConverter<T> jsonConverter;

    protected Response(Message message, JsonConverter<T> jsonConverter) {
        if (message == null) {
            throw new NullPointerException("'message' cannot be null");
        }
        if (!message.isResponse()) {
            throw new IllegalArgumentException("'message' must be a response message:\n" + JsonUtils.write(message));
        }
        if (jsonConverter == null) {
            throw new NullPointerException("'jsonConverter' cannot be null");
        }

        this.message = message;
        this.jsonConverter = jsonConverter;
    }

    /**
     * Gets the roundtrip time for the message.
     *
     * @return The roundtrip time.
     */
    public final long getRoundtripMillis() {
        return message.getReceivedMillis() - message.getSentMillis();
    }

    /**
     * Gets the processing time for the message on in the service.
     *
     * @return The processing time for the message.
     */
    public final long getProcessingMillis() {
        return message.getStopProcessingMillis() - message.getStartProcessingMillis();
    }

    /**
     * Checks whether or not the message represents a successful response.
     *
     * @return True if the response represents a successful response, otherwise
     * false if the response represents an error.
     */
    public final boolean isSuccessful() {
        return !message.isError();
    }

    /**
     * Gets the response contents if the message represents a successful
     * response.
     *
     * @return The response contents if the message represents a successful
     * response, otherwise null.
     *
     * NOTE: Do not rely on a null response for determining an error - the
     * successful response itself may be a null value. Instead, check for
     * success or failure using the {@link Response#isSuccessful() } method.
     */
    public final T getResponse() {
        if (!this.isSuccessful()) {
            return null;
        }

        T result = jsonConverter.deserialize(message.getBody());
        return result;
    }

    /**
     * Gets the error if the message represents an error response. This method
     * will throw an exception if the error message is not a JSON serialized
     * {@link SafeErrorDetails }.
     *
     * @return The error if the message represents an error response, otherwise
     * null.
     *
     * NOTE: Do not rely on a null response for determining success - the error
     * response itself may be a null value. Instead, check for success or
     * failure using the {@link Response#isSuccessful() } method.
     */
    public final SafeErrorDetails getError() {
        if (this.isSuccessful()) {
            return null;
        }

        String errorBody = message.getBody() == null ? null : message.getBody().getValue();
        SafeErrorDetails error = JsonUtils.read(errorBody, new TypeInfo<SafeErrorDetails>() {});
        return error;
    }

    /**
     * Gets the error if the message represents an error response. This method
     * will not attempt to deserialize the response body into a
     * {@link SafeErrorDetails }. Use this method when calling custom service
     * implementations that do not always return a JSON serialized
     * {@link SafeErrorDetails } for error responses.
     *
     * @return The error if the message represents an error response, otherwise
     * null.
     * <p>
     *     NOTE: Do not rely on a null response for determining success - the
     *     error response itself may be a null value. Instead, check for success
     *     or failure using the {@link Response#isSuccessful() } method.
     * </p>
     */
    public final RawJsonString getRawError() {
        if (this.isSuccessful()) {
            return null;
        }

        RawJsonString error = message.getBody();
        return error;
    }

    /**
     * Throws the error if this represents an error response, otherwise returns
     * this Response object.
     *
     * @return This response object.
     */
    public final Response<T> throwIfError() {
        try {
            SafeErrorDetails thr = this.getError();
            if (thr != null) {
                throw new WebSocketRequestException(thr.getError(), new RuntimeException(String.join("\n", thr.getReasons())));
            }
        } catch (Exception ignored) {
            // Swallow the exception since we couldn't deserialize the response,
            // and just throw a new exception with the raw error as the message
            RawJsonString error = this.getRawError();
            throw new WebSocketRequestException(error == null ? null : error.getValue());
        }

        return this;
    }
}
