package roramu.service.websocket.jetty;

import roramu.service.websocket.MessageHandler;
import roramu.service.websocket.TypedMessageHandler;
import roramu.service.websocket.WebSocketService;
import roramu.util.reflection.TypeInfo;

import java.util.Map;
import java.util.function.Function;

public class EchoService extends WebSocketService {
    private static TypedMessageHandler<Object, Object> ECHO_HANDLER = TypedMessageHandler.create(
        TypeInfo.OBJECT,
        TypeInfo.OBJECT,
        Function.identity());

    @Override
    protected Map<String, MessageHandler> createMessageHandlers() {
        Map<String, MessageHandler> result = super.createMessageHandlers();
        result.put("ECHO", ECHO_HANDLER);

        return result;
    }
}
