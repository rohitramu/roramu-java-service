package roramu.service.websocket;

import roramu.util.async.AsyncUtils;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class WebSocketServiceProxy<T extends WebSocketClient> {
    private static final long RETRY_MS_INIT = 50;
    private static final float RETRY_BACKOFF_MULTIPLIER = 1.5f;
    private static final int DEFAULT_MAX_RETRIES = 10;

    private final String name;
    private final Class<T> clientImplementation;
    private final Supplier<T> clientFactory;
    private T client = null;

    public static <T extends WebSocketClient> WebSocketServiceProxy<T> create(String name, Class<T> clientImplementation) {
        return WebSocketServiceProxy.create(name, clientImplementation, () -> WebSocketClient.connect(clientImplementation));
    }

    public static <T extends WebSocketClient> WebSocketServiceProxy<T> create(String name, Class<T> clientImplementation, URI serviceAddress) {
        if (serviceAddress == null) {
            throw new IllegalArgumentException("'serviceAddress' cannot be null");
        }

        return WebSocketServiceProxy.create(name, clientImplementation, () -> WebSocketClient.connect(clientImplementation, serviceAddress));
    }

    public static <T extends WebSocketClient> WebSocketServiceProxy<T> create(String name, Class<T> clientImplementation, Supplier<T> clientFactory) {
        if (name == null) {
            throw new IllegalArgumentException("'name' cannot be null");
        }
        if (clientImplementation == null) {
            throw new IllegalArgumentException("'clientImplementation' cannot be null");
        }
        if (clientFactory == null) {
            throw new IllegalArgumentException("'clientFactory' cannot be null");
        }

        return new WebSocketServiceProxy<>(name, clientImplementation, clientFactory);
    }

    private WebSocketServiceProxy(String name, Class<T> clientImplementation, Supplier<T> clientFactory) {
        this.name = name;
        this.clientImplementation = clientImplementation;
        this.clientFactory = clientFactory;
    }

    public String getName() {
        return this.name;
    }

    public Class<T> getClientImplementation() {
        return this.clientImplementation;
    }

    public CompletableFuture<T> getClientAsync() {
        return this.getClientAsync(WebSocketServiceProxy.DEFAULT_MAX_RETRIES);
    }

    public CompletableFuture<T> getClientAsync(int maxRetries) {
        // If we already have a working connection, return it
        if (client != null && client.isOpen()) {
            return CompletableFuture.completedFuture(this.client);
        }

        // Create the connection
        return AsyncUtils.startTask(() -> {
            long currentBackoff = RETRY_MS_INIT;
            int numRetries = 0;
            while (numRetries < maxRetries) {
                AtomicReference<InterruptedException> interruptedException = new AtomicReference<>(null);
                try {
                    this.client = this.clientFactory.get();
                    return this.client;
                } catch (Exception ex) {
                    //TODO: log

                    InterruptedException exInterrupted = interruptedException.get();
                    if (exInterrupted == null) {
                        // Swallow exception and wait for some time before we retry the connection attempt
                        // Force one last attempt before giving up by suppressing the exception and storing it for later
                        AsyncUtils.sleep(currentBackoff, TimeUnit.MILLISECONDS, interruptedException::set);
                        currentBackoff *= RETRY_BACKOFF_MULTIPLIER;
                    } else {
                        // The final attempt failed
                        throw new RuntimeException("Thread was interrupted before a successful connection could be made for the service proxy '" + this.getName() +
                            "', using the client implementation '" + this.getClientImplementation() + "'", exInterrupted);
                    }
                }

                numRetries++;
            }

            throw new RuntimeException("Failed to make a successful connection after " + maxRetries + " attempts for the service proxy '" + this.getName() +
                "', using the client implementation '" + this.getClientImplementation() + "'");
        });
    }
}
