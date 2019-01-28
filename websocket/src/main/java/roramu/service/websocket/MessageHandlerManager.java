package roramu.service.websocket;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class MessageHandlerManager {
    private final Map<String, MessageHandler> messageHandlers = new ConcurrentHashMap<>();

    public MessageHandlerManager() {}

    /**
     * Gets the registered message types.
     *
     * @return The registered message types.
     */
    public Set<String> getMessageTypes() {
        return this.messageHandlers.keySet();
    }

    /**
     * Gets a message handler.
     *
     * @param messageType The message type.
     * @return The message handler if it exists, otherwise null.
     */
    public MessageHandler get(String messageType) {
        if (messageType == null) {
            throw new IllegalArgumentException("'name' cannot be null");
        }

        return this.messageHandlers.get(messageType);
    }

    /**
     * Adds or updates a message handler.
     *
     * @param messageType The message type.
     * @param messageHandler The message handler.
     */
    public void set(String messageType, MessageHandler messageHandler) {
        if (messageType == null) {
            throw new IllegalArgumentException("'name' cannot be null");
        }
        if (messageHandler == null) {
            throw new IllegalArgumentException("'messageHandler' cannot be null");
        }

        this.messageHandlers.put(messageType, messageHandler);
    }

    /**
     * Adds or updates a typed message handler.
     *
     * @param messageType The message type.
     * @param handler The message handler.
     * @param <Req> The request type.
     */
    public <Req> void set(MessageType<Req, Void> messageType, Consumer<Req> handler) {
        if (messageType == null) {
            throw new IllegalArgumentException("'messageType' cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("'handler' cannot be null");
        }

        MessageHandler messageHandler = TypedMessageHandler.create(messageType, handler);
        this.set(messageType.getName(), messageHandler);
    }

    /**
     * Adds or updates a typed message handler.
     *
     * @param messageType The message type.
     * @param handler The message handler.
     * @param <Res> The response type.
     */
    public <Res> void set(MessageType<Void, Res> messageType, Supplier<Res> handler) {
        if (messageType == null) {
            throw new IllegalArgumentException("'messageType' cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("'handler' cannot be null");
        }

        MessageHandler messageHandler = TypedMessageHandler.create(messageType, handler);
        this.set(messageType.getName(), messageHandler);
    }

    /**
     * Adds or updates a typed message handler.
     *
     * @param messageType The message type.
     * @param handler The message handler.
     * @param <Req> The request type.
     * @param <Res> The response type.
     */
    public <Req, Res> void set(MessageType<Req, Res> messageType, Function<Req, Res> handler) {
        if (messageType == null) {
            throw new IllegalArgumentException("'messageType' cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("'handler' cannot be null");
        }

        MessageHandler messageHandler = TypedMessageHandler.create(messageType, handler);
        this.set(messageType.getName(), messageHandler);
    }

    /**
     * Removes a message handler.
     *
     * @param messageType The message type.
     * @return The message handler that was removed if the removal was successful, otherwise null.
     */
    public MessageHandler remove(String messageType) {
        if (messageType == null) {
            throw new IllegalArgumentException("'messageType' cannot be null");
        }

        return this.messageHandlers.remove(messageType);
    }
}
