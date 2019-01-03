package roramu.service.websocket.jetty;

import roramu.service.websocket.MessageHandler;
import roramu.service.websocket.TypedMessageHandler;
import roramu.service.websocket.WebSocketService;

import java.util.Map;
import java.util.function.Function;

public class TestService extends WebSocketService {
    private static TypedMessageHandler<Object, Object> ECHO_HANDLER = TypedMessageHandler.create(
        TestMessageTypes.ECHO,
        Function.identity());

    @Override
    protected Map<String, MessageHandler> createMessageHandlers() {
        Map<String, MessageHandler> result = super.createMessageHandlers();
        result.put(TestMessageTypes.ECHO.getName(), ECHO_HANDLER);

        return result;
    }
}
