package roramu.service.websocket;

public class WebSocketEndpointConfiguration {
    private MessageHandlerManager messageHandlers;

    public WebSocketEndpointConfiguration() {
        this.messageHandlers = new MessageHandlerManager();
    }

    public MessageHandlerManager getMessageHandlers() {
        return messageHandlers;
    }

    protected void setMessageHandlers(MessageHandlerManager messageHandlers) {
        if (messageHandlers == null) {
            throw new IllegalArgumentException("'messageHandlers' cannot be null");
        }

        this.messageHandlers = messageHandlers;
    }
}
