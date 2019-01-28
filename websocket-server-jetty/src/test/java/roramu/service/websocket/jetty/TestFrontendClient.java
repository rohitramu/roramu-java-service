package roramu.service.websocket.jetty;

import roramu.service.websocket.Response;
import roramu.service.websocket.WebSocketClient;

import java.net.URI;

public class TestFrontendClient extends WebSocketClient {
    public Response<Object> echo(Object message) {
        return super.sendRequest(TestMessageTypes.ECHO, message);
    }

    public Response<String> greet(String name) {
        return super.sendRequest(TestMessageTypes.GREET, name);
    }

    @Override
    public URI getDefaultServiceAddress() {
        // The tests themselves must specify the service address
        return null;
    }
}
