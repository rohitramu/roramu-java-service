package roramu.service.websocket.jetty;

import roramu.service.websocket.Response;
import roramu.service.websocket.WebSocketClient;

public class TestClient extends WebSocketClient {
    public Response<Object> sendRequest(Object message) {
        return super.sendRequest(TestMessageTypes.ECHO, message);
    }
}
