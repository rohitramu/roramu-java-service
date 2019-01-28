package roramu.service.websocket;

public class WebSocketServiceConfiguration extends WebSocketEndpointConfiguration {
    private WebSocketServiceProxyManager serviceProxies = new WebSocketServiceProxyManager();

    public WebSocketServiceConfiguration() {
        super();
    }

    public WebSocketServiceConfiguration(WebSocketEndpointConfiguration endpointConfiguration) {
        this.setMessageHandlers(endpointConfiguration.getMessageHandlers());
    }

    public WebSocketServiceProxyManager getServiceProxies() {
        return serviceProxies;
    }

    protected void setServiceProxies(WebSocketServiceProxyManager serviceProxies) {
        if (serviceProxies == null) {
            throw new IllegalArgumentException("'serviceProxies' cannot be null");
        }

        this.serviceProxies = serviceProxies;
    }
}
