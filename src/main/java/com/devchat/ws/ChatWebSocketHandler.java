package com.devchat.ws;

import com.devchat.security.TokenService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Optional;


@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String USERNAME_ATTR = "username";

    private final TokenService tokens;

    private final WsRegistry registry;


    public ChatWebSocketHandler(TokenService tokens, WsRegistry registry) {
        this.tokens = tokens;
        this.registry = registry;
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        Optional<String> username = tokenFrom(session).flatMap(tokens::verify);

        if (username.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid or missing token"));
            return;
        }

        String name = username.get();
        session.getAttributes().put(USERNAME_ATTR, name);
        registry.add(name, session);

        session.sendMessage(new TextMessage("{\"type\":\"ready\",\"username\":\"" + name + "\"}"));
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        Object username = session.getAttributes().get(USERNAME_ATTR);

        if (username != null) {
            registry.remove(username.toString(), session);
        }
    }


    private Optional<String> tokenFrom(WebSocketSession session) {

        URI uri = session.getUri();

        if (uri == null || uri.getQuery() == null) {
            return Optional.empty();
        }

        String[] pairs = uri.getQuery().split("&");

        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq > 0 && "token".equals(pair.substring(0, eq))) {
                String decoded = java.net.URLDecoder.decode(pair.substring(eq + 1),
                        java.nio.charset.StandardCharsets.UTF_8);
                return Optional.of(decoded);
            }
        }

        return Optional.empty();
    }
}
