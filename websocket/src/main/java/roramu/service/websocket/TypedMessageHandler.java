package roramu.service.websocket;

import roramu.util.json.JsonConverter;
import roramu.util.json.RawJsonString;
import roramu.util.json.SimpleJsonConverter;
import roramu.util.reflection.TypeInfo;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A message handler which accepts JSON requests and returns JSON responses.
 *
 * @param <Req> The request type.
 * @param <Res> The response type.
 */
public final class TypedMessageHandler<Req, Res> implements MessageHandler {
    private static final SimpleJsonConverter<Void> JSON_CONVERTER_VOID = new SimpleJsonConverter<>(TypeInfo.VOID);

    private TypeInfo<Req> requestType;
    private TypeInfo<Res> responseType;
    private JsonConverter<Req> requestJsonConverter;
    private JsonConverter<Res> responseJsonConverter;
    private Function<Req, Res> handleTypedMessage;

    private TypedMessageHandler() {}

    /**
     * Constructs a new TypedMessageHandler object for methods that don't
     * require a request body.
     *
     * @param <Res> The response type.
     * @param messageType The message type which defines the request/response
     * types as well as the converters to serialize/deserialize these types.
     * @param handleTypedMessage The function to handle the deserialized
     * message.
     *
     * @return The message handler.
     */
    public static <Res> TypedMessageHandler<Void, Res> create(MessageType<Void, Res> messageType, Supplier<Res> handleTypedMessage) {
        if (messageType == null) {
            throw new NullPointerException("'messageType' cannot be null");
        }
        if (handleTypedMessage == null) {
            throw new NullPointerException("'handleTypedMessage' cannot be null");
        }

        // Convert the supplier into a function with an unused Void parameter
        return create(
            messageType,
            (Void notUsed) -> handleTypedMessage.get());
    }

    /**
     * Constructs a new TypedMessageHandler object.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param messageType The message type which defines the request/response
     * types as well as the converters to serialize/deserialize these types.
     * @param handleTypedMessage The function to handle the deserialized
     * message.
     *
     * @return The message handler.
     */
    public static <Req, Res> TypedMessageHandler<Req, Res> create(MessageType<Req, Res> messageType, Function<Req, Res> handleTypedMessage) {
        if (messageType == null) {
            throw new NullPointerException("'messageType' cannot be null");
        }
        if (handleTypedMessage == null) {
            throw new NullPointerException("'handleTypedMessage' cannot be null");
        }

        return create(
            messageType.getRequestType(), messageType.getRequestJsonConverter(),
            messageType.getResponseType(), messageType.getResponseJsonConverter(),
            handleTypedMessage);
    }

    /**
     * Constructs a new TypedMessageHandler object for methods that don't require
     * a request body. Both the request and response types will use the
     * {@link SimpleJsonConverter} to serialize and deserialize requests/responses.
     *
     * @param <Res> The response type.
     * @param responseType The type of object returned in the response body.
     * @param handleTypedMessage The function to handle the deserialized
     * message.
     *
     * @return The message handler.
     */
    public static <Res> TypedMessageHandler<Void, Res> create(TypeInfo<Res> responseType, Supplier<Res> handleTypedMessage) {
        if (responseType == null) {
            throw new NullPointerException("'responseType' cannot be null");
        }
        if (handleTypedMessage == null) {
            throw new NullPointerException("'handleTypedMessage' cannot be null");
        }

        // Convert the supplier into a function with an unused Void parameter
        return create(
                TypeInfo.VOID,
                responseType,
                (Void notUsed) -> handleTypedMessage.get());
    }

    /**
     * Constructs a new TypedMessageHandler object. Both the request and
     * response types will use the {@link SimpleJsonConverter} to serialize and
     * deserialize requests/responses.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param requestType The type of object received in the request body.
     * @param responseType The type of object returned in the response body.
     * @param handleTypedMessage The function to handle the deserialized
     * message.
     *
     * @return The message handler.
     */
    public static <Req, Res> TypedMessageHandler<Req, Res> create(TypeInfo<Req> requestType, TypeInfo<Res> responseType, Function<Req, Res> handleTypedMessage) {
        if (requestType == null) {
            throw new NullPointerException("'requestType' cannot be null");
        }
        if (responseType == null) {
            throw new NullPointerException("'responseType' cannot be null");
        }
        if (handleTypedMessage == null) {
            throw new NullPointerException("'handleTypedMessage' cannot be null");
        }

        SimpleJsonConverter<Req> requestJsonConverter = new SimpleJsonConverter<>(requestType);
        SimpleJsonConverter<Res> responseJsonConverter = new SimpleJsonConverter<>(responseType);

        return create(
            requestType, requestJsonConverter,
            responseType, responseJsonConverter,
            handleTypedMessage);
    }

    /**
     * Constructs a new TypedMessageHandler object.
     *
     * @param <Res> The response type.
     * @param responseType The type of object returned in the response body.
     * @param responseJsonConverter The converter used to convert the response
     * object into the response body.
     * @param handleTypedMessage The function to handle the deserialized
     * message.
     *
     * @return The message handler.
     */
    public static <Res> TypedMessageHandler<Void, Res> create(
            TypeInfo<Res> responseType,
            JsonConverter<Res> responseJsonConverter,
            Supplier<Res> handleTypedMessage
    ) {
        if (responseType == null) {
            throw new NullPointerException("'responseType' cannot be null");
        }
        if (responseJsonConverter == null) {
            throw new NullPointerException("'responseJsonConverter' cannot be null");
        }
        if (handleTypedMessage == null) {
            throw new NullPointerException("'handleTypedMessage' cannot be null");
        }

        return create(
            TypeInfo.VOID,
            JSON_CONVERTER_VOID,
            responseType,
            responseJsonConverter,
            (Void notUsed) -> handleTypedMessage.get());
    }

    /**
     * Constructs a new TypedMessageHandler object.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param requestType The type of object received in the request body.
     * @param requestJsonConverter The converter used to convert the request
     * body into the request object.
     * @param responseType The type of object returned in the response body.
     * @param responseJsonConverter The converter used to convert the response
     * object into the response body.
     * @param handleTypedMessage The function to handle the deserialized
     * message.
     *
     * @return The message handler.
     */
    public static <Req, Res> TypedMessageHandler<Req, Res> create(
        TypeInfo<Req> requestType,
        JsonConverter<Req> requestJsonConverter,
        TypeInfo<Res> responseType,
        JsonConverter<Res> responseJsonConverter,
        Function<Req, Res> handleTypedMessage
    ) {
        if (requestType == null) {
            throw new NullPointerException("'requestType' cannot be null");
        }
        if (responseType == null) {
            throw new NullPointerException("'responseType' cannot be null");
        }
        if (requestJsonConverter == null) {
            throw new NullPointerException("'requestJsonConverter' cannot be null");
        }
        if (responseJsonConverter == null) {
            throw new NullPointerException("'responseJsonConverter' cannot be null");
        }
        if (handleTypedMessage == null) {
            throw new NullPointerException("'handleTypedMessage' cannot be null");
        }

        TypedMessageHandler<Req, Res> result = new TypedMessageHandler<>();
        result.requestType = requestType;
        result.responseType = responseType;
        result.requestJsonConverter = requestJsonConverter;
        result.responseJsonConverter = responseJsonConverter;
        result.handleTypedMessage = handleTypedMessage;

        return result;
    }

    /**
     * The type of the deserialized request.
     *
     * @return The type of the deserialized request.
     */
    public TypeInfo<Req> getRequestType() {
        return this.requestType;
    }

    /**
     * The type of the deserialized response.
     *
     * @return The type of the deserialized response.
     */
    public TypeInfo<Res> getResponseType() {
        return this.responseType;
    }

    /**
     * The {@link JsonConverter} used to serialize and deserialize the request
     * type.
     *
     * @return The JsonConverter.
     */
    public JsonConverter<Req> getRequestJsonConverter() {
        return requestJsonConverter;
    }

    /**
     * The {@link JsonConverter} used to serialize and deserialize the response
     * type.
     *
     * @return The JsonConverter.
     */
    public JsonConverter<Res> getResponseJsonConverter() {
        return responseJsonConverter;
    }

    @Override
    public final RawJsonString handleMessage(RawJsonString message) {
        Req request;
        if (message == null) {
            request = null;
        } else {
            request = this.requestJsonConverter.deserialize(message);
        }

        Res response = this.handleTypedMessage(request);
        RawJsonString jsonResponse = this.responseJsonConverter.serialize(response);

        return jsonResponse;
    }

    /**
     * Handles a deserialized request body.
     *
     * @param request The deserialized request body.
     *
     * @return The response.
     */
    public final Res handleTypedMessage(Req request) {
        return this.handleTypedMessage.apply(request);
    }
}
