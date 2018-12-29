package roramu.service.websocket;

public class WebSocketRequestException extends RuntimeException {
    public WebSocketRequestException() {
        super();
    }

    public WebSocketRequestException(String message) {
        super(message);
    }

    public WebSocketRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
