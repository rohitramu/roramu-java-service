package roramu.service.websocket.jetty;

import roramu.service.websocket.MessageType;
import roramu.util.reflection.TypeInfo;

public class TestMessageTypes {
    public static final MessageType<Object, Object> ECHO = MessageType.create("ECHO", TypeInfo.OBJECT, TypeInfo.OBJECT);
}
