package roramu.service.websocket;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@FunctionalInterface
public interface WebSocketHandshakeFilter {
    void doFilter(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
}
