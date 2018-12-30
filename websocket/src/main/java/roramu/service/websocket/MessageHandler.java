package roramu.service.websocket;

import roramu.util.json.RawJsonString;

/**
 * Contract for a message handler.
 * <p>
 * This method should be able to handle null messages.
 */
@FunctionalInterface
public interface MessageHandler {
    /**
     * Processes a raw JSON request body.
     *
     * @param message The JSON request body.
     * @return The JSON response body.
     */
    RawJsonString handleMessage(RawJsonString message);
}
