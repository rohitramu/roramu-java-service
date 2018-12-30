package roramu.service.websocket;

public interface WebSocketServerLifecycleManager {
    /**
     * Starts the service.
     */
    void start();

    /**
     * Stops the service.
     */
    void stop();

    /**
     * Determines whether the service is running or not.
     *
     * @return True if the service is running, false otherwise.
     */
    boolean isRunning();

    /**
     * Joins the current thread to the thread that the service
     * is running on.
     */
    void join();
}
