package roramu.service.websocket;

import roramu.service.status.ServiceStatus;
import roramu.util.reflection.TypeInfo;

/**
 * The built-in message types
 */
public final class BuiltInMessageTypes {
    private BuiltInMessageTypes() {}

    public static final boolean isResponse(String messageType) {
        return Response.RESPONSE.getName().equals(messageType) || BuiltInMessageTypes.isError(messageType);
    }

    public static final boolean isError(String messageType) {
        return Response.ERROR.getName().equals(messageType);
    }

    public static final class Response {
        public static final MessageType<Object, Void> RESPONSE = MessageType.create("RESPONSE", new TypeInfo<Object>() {}, TypeInfo.VOID);
        public static final MessageType<Object, Void> ERROR = MessageType.create("ERROR", new TypeInfo<Object>() {}, TypeInfo.VOID);

        private Response() {}
    }

    public static final class System {
        public static final MessageType<Object, ServiceStatus> STATUS = MessageType.create("STATUS", new TypeInfo<Object>() {}, new TypeInfo<ServiceStatus>() {});
        public static final MessageType<String, Void> DEPENDENCY_UPDATED = MessageType.create("DEPENDENCY_UPDATED", new TypeInfo<String>() {}, TypeInfo.VOID);
        public static final MessageType<Void, Void> CLOSE_ALL_SESSIONS = MessageType.create("CLOSE_ALL_SESSIONS", TypeInfo.VOID, TypeInfo.VOID);

        private System() {}
    }
}
