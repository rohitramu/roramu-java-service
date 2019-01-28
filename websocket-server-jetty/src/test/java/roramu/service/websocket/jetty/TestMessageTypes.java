package roramu.service.websocket.jetty;

import roramu.service.websocket.MessageType;
import roramu.util.reflection.TypeInfo;

public class TestMessageTypes {
    public static final MessageType<Object, Object> ECHO = MessageType.create("ECHO", TypeInfo.OBJECT, TypeInfo.OBJECT);
    public static final MessageType<String, String> GREET = MessageType.create("GREET", TypeInfo.STRING, TypeInfo.STRING);
}
