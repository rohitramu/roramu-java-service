package roramu.service.websocket;

import roramu.util.json.JsonConverter;
import roramu.util.json.SimpleJsonConverter;
import roramu.util.reflection.TypeInfo;

/**
 * A message type that a service can handle.
 *
 * @param <Req> The request type. This should be an in-built Java type
 * or a POJO, as it will be serialized and deserialized to/from JSON.
 * @param <Res> The response type. This should be an in-built Java
 * type or a POJO, as it will be serialized and deserialized to/from JSON.
 */
public final class MessageType<Req, Res> {
    private String name;
    private TypeInfo<Req> requestType;
    private TypeInfo<Res> responseType;
    private JsonConverter<Req> requestJsonConverter;
    private JsonConverter<Res> responseJsonConverter;

    private MessageType() {}

    /**
     * A message type that a service can handle. This uses the
     * {@link SimpleJsonConverter} implementation for request and response
     * objects.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param name The name of the message type.
     * @param requestType The request type reference. This should be referencing
     * an in-built Java type or a POJO, as it will be serialized and
     * deserialized to/from JSON. If no request body is required for this
     * handler, {@link TypeInfo#VOID TypeInfo.VOID} should be used.
     * @param responseType The response type reference. This should be
     * referencing an in-built Java type or a POJO, as it will be serialized and
     * deserialized to/from JSON. If no response body is required for this
     * handler, {@link TypeInfo#VOID TypeInfo.VOID} should be used.
     * @return The message type.
     */
    public static <Req, Res> MessageType<Req, Res> create(String name, TypeInfo<Req> requestType, TypeInfo<Res> responseType) {
        if (name == null) {
            throw new NullPointerException("'name' cannot be null");
        }
        if (requestType == null) {
            throw new NullPointerException("'requestType' cannot be null");
        }
        if (responseType == null) {
            throw new NullPointerException("'responseType' cannot be null");
        }

        SimpleJsonConverter<Req> requestJsonConverter = new SimpleJsonConverter<>(requestType);
        SimpleJsonConverter<Res> responseJsonConverter = new SimpleJsonConverter<>(responseType);
        return create(name,
            requestType, requestJsonConverter,
            responseType, responseJsonConverter
        );
    }

    /**
     * A message type that a service can handle. This uses the
     * {@link SimpleJsonConverter} implementation for serializing and
     * deserializing request objects.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param name The name of the message type.
     * @param requestType The request type reference. This should be referencing
     * an in-built Java type or a POJO, as it will be serialized and
     * deserialized to/from JSON. If no request body is required for this
     * handler, {@link TypeInfo#VOID TypeInfo.VOID} should be used.
     * @param responseType The request type reference. This should be
     * referencing an in-built Java type or a POJO, as it will be serialized and
     * deserialized to/from JSON. If no request body is required for this
     * handler, {@link TypeInfo#VOID TypeInfo.VOID} should be used.
     * @param responseJsonConverter The JsonConverter that will be used to
     * serialize and deserialize response objects.
     * @return The message type.
     */
    public static <Req, Res> MessageType<Req, Res> create(String name, TypeInfo<Req> requestType, TypeInfo<Res> responseType, JsonConverter<Res> responseJsonConverter) {
        if (name == null) {
            throw new NullPointerException("'name' cannot be null");
        }
        if (requestType == null) {
            throw new NullPointerException("'requestType' cannot be null");
        }
        if (responseType == null) {
            throw new NullPointerException("'responseType' cannot be null");
        }
        if (responseJsonConverter == null) {
            throw new NullPointerException("'responseJsonConverter' cannot be null");
        }

        SimpleJsonConverter<Req> requestJsonConverter = new SimpleJsonConverter<>(requestType);
        return create(name,
            requestType, requestJsonConverter,
            responseType, responseJsonConverter
        );
    }

    /**
     * A message type that a service can handle. This uses the
     * {@link SimpleJsonConverter} implementation for serializing and
     * deserializing response objects.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param name The name of the message type.
     * @param requestType The request type reference. This should be referencing
     * an in-built Java type or a POJO, as it will be serialized and
     * deserialized to/from JSON. If no request body is required for this
     * handler, {@link TypeInfo#VOID TypeInfo.VOID} should be used.
     * @param requestJsonConverter The JsonConverter that will be used to
     * serialize and deserialize request objects.
     * @param responseType The request type reference. This should be
     * referencing an in-built Java type or a POJO, as it will be serialized and
     * deserialized to/from JSON. If no request body is required for this
     * handler, {@link TypeInfo#VOID TypeInfo.VOID} should be used.
     * @return The message type.
     */
    public static <Req, Res> MessageType<Req, Res> create(String name, TypeInfo<Req> requestType, JsonConverter<Req> requestJsonConverter, TypeInfo<Res> responseType) {
        if (name == null) {
            throw new NullPointerException("'name' cannot be null");
        }
        if (requestType == null) {
            throw new NullPointerException("'requestType' cannot be null");
        }
        if (requestJsonConverter == null) {
            throw new NullPointerException("'requestJsonConverter' cannot be null");
        }
        if (responseType == null) {
            throw new NullPointerException("'responseType' cannot be null");
        }

        SimpleJsonConverter<Res> responseJsonConverter = new SimpleJsonConverter<>(responseType);
        return create(name,
            requestType, requestJsonConverter,
            responseType, responseJsonConverter
        );
    }

    /**
     * A message type that a service can handle. This overload should be used
     * when the request and response types are not correctly serialized or
     * deserialized by the {@link SimpleJsonConverter} implementation.
     *
     * @param <Req> The request type.
     * @param <Res> The response type.
     * @param name The name of the message type.
     * @param requestType The request type reference. This should be referencing
     * an in-built Java type or a POJO, as it will be serialized and
     * deserialized to/from JSON. If no request body is required for this
     * handler, {@link TypeInfo#VOID TypeInfo.VOID} should be used.
     * @param requestJsonConverter The JsonConverter that will be used to
     * serialize and deserialize request objects.
     * @param responseJsonConverter The JsonConverter that will be used to
     * serialize and deserialize response objects.
     * @param responseType The request type reference. This should be
     * referencing an in-built Java type or a POJO, as it will be serialized and
     * deserialized to/from JSON. If no request body is required for this
     * handler, {@link TypeInfo#VOID TypeInfo.VOID} should be used.
     * @return The message type.
     */
    public static <Req, Res> MessageType<Req, Res> create(
        String name,
        TypeInfo<Req> requestType, JsonConverter<Req> requestJsonConverter,
        TypeInfo<Res> responseType, JsonConverter<Res> responseJsonConverter
    ) {
        if (name == null) {
            throw new NullPointerException("'name' cannot be null");
        }
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

        MessageType<Req, Res> result = new MessageType<>();
        result.name = name;
        result.requestType = requestType;
        result.responseType = responseType;
        result.requestJsonConverter = requestJsonConverter;
        result.responseJsonConverter = responseJsonConverter;

        return result;
    }

    /**
     * The name of the message type.
     *
     * @return The name of the message type.
     */
    public String getName() {
        return name;
    }

    /**
     * The request type.
     *
     * @return The request type.
     */
    public TypeInfo<Req> getRequestType() {
        return this.requestType;
    }

    /**
     * The response type.
     *
     * @return The response type.
     */
    public TypeInfo<Res> getResponseType() {
        return this.responseType;
    }

    /**
     * The JsonConverter which will be used to serialize and deserialize request
     * objects.
     *
     * @return The JsonConverter.
     */
    public JsonConverter<Req> getRequestJsonConverter() {
        return this.requestJsonConverter;
    }

    /**
     * The JsonConverter which will be used to serialize and deserialize
     * response objects.
     *
     * @return The JsonConverter.
     */
    public JsonConverter<Res> getResponseJsonConverter() {
        return this.responseJsonConverter;
    }
}
