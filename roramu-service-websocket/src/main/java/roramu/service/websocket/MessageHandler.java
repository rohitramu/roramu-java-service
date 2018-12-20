package roramu.service.websocket;

/**
 * Contract for a message handler.
 *
 * This method should be able to handle null messages.
 */
@FunctionalInterface
public interface MessageHandler {
    /**
     * Processes a raw JSON request body.
     *
     * @param message The JSON request body.
     *
     * @return The JSON response body.
     */
    String handleMessage(String message);
}
