package roramu.service.websocket;

public class WebSocketClientConfiguration extends WebSocketEndpointConfiguration {
    public WebSocketClientConfiguration(WebSocketEndpointConfiguration endpointConfiguration) {
        this.setMessageHandlers(endpointConfiguration.getMessageHandlers());
    }
}
