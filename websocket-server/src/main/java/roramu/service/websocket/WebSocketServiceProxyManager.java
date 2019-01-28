package roramu.service.websocket;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycles of WebSocket service proxies.
 */
public class WebSocketServiceProxyManager {
    private final Map<String, WebSocketServiceProxy<? extends WebSocketClient>> proxies = new ConcurrentHashMap<>();

    public WebSocketServiceProxyManager() {}

    /**
     * Gets the name of each registered proxy.
     *
     * @return The name of each registered proxy.
     */
    public Set<String> getNames() {
        return this.proxies.keySet();
    }

    /**
     * Gets the client implementation that a proxy manages.
     *
     * @param proxyName The name of the proxy.
     * @return The client implementation that the proxy manages.
     */
    public Class<? extends WebSocketClient> getType(String proxyName) {
        // Try to get the proxy
        WebSocketServiceProxy<?> retrievedProxy = this.proxies.get(proxyName);

        // If the proxy doesn't exist, return null
        if (retrievedProxy == null) {
            return null;
        }

        return retrievedProxy.getClientImplementation();
    }

    /**
     * Gets a proxy.
     *
     * @param proxyName The name of the proxy.
     * @param clientImplementation The client implementation that is managed by the proxy.
     * @param <T> The client implementation that is managed by the proxy.
     * @return The proxy if it exists, otherwise null.
     */
    public <T extends WebSocketClient> WebSocketServiceProxy<T> get(String proxyName, Class<T> clientImplementation) {
        if (proxyName == null) {
            throw new IllegalArgumentException("'proxyName' cannot be null");
        }
        if (clientImplementation == null) {
            throw new IllegalArgumentException("'clientImplementation' cannot be null");
        }

        // Try to get the proxy
        WebSocketServiceProxy<?> retrievedProxy = this.proxies.get(proxyName);

        // If the proxy doesn't exist, return null
        if (retrievedProxy == null) {
            return null;
        }

        // Make sure that the proxy's client implementation is of the same type as what was requested
        Class<?> proxyClientImplementation = retrievedProxy.getClientImplementation();
        if (!proxyClientImplementation.equals(clientImplementation)) {
            String expectedType = clientImplementation.getPackageName() + "." + clientImplementation.getSimpleName();
            String actualType = proxyClientImplementation.getPackageName() + "." + proxyClientImplementation.getSimpleName();

            throw new ClassCastException("Expected the client implementation in the proxy named '" + proxyName + "' to be of type '" + expectedType + "', but it is of type '" + actualType + "'");
        }

        // Cast the proxy
        @SuppressWarnings("unchecked")
        WebSocketServiceProxy<T> castedProxy = (WebSocketServiceProxy<T>)retrievedProxy;

        return castedProxy;
    }

    /**
     * Adds or replaces a proxy based on the proxy's name (i.e. determined by {@link WebSocketServiceProxy#getName()}).
     *
     * @param proxy The proxy.
     * @param <T> The client implementation that is managed by the proxy.
     */
    public <T extends WebSocketClient> void set(WebSocketServiceProxy<T> proxy) {
        if (proxy == null) {
            throw new IllegalArgumentException("'proxy' cannot be null");
        }

        // Make sure "remove" cannot be called while the proxy is being set
        synchronized (this.proxies) {
            this.proxies.put(proxy.getName(), proxy);
        }
    }

    /**
     * Removes a proxy.
     *
     * @param proxy The proxy to remove.
     * @param <T> The client implementation that is managed by the proxy.
     * @return True if the proxy was removed, otherwise false.
     */
    public <T extends WebSocketClient> boolean remove(WebSocketServiceProxy<T> proxy) {
        if (proxy == null) {
            throw new IllegalArgumentException("'proxy' cannot be null");
        }

        return this.proxies.remove(proxy.getName(), proxy);
    }
}
