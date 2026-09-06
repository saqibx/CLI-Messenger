package com.devchat.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class WsRegistry {

    private static final Logger log = LoggerFactory.getLogger(WsRegistry.class);

    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();


    public void add(String username, WebSocketSession session) {
        sessions.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(session);
    }


    public void remove(String username, WebSocketSession session) {

        Set<WebSocketSession> userSessions = sessions.get(username);

        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessions.remove(username, userSessions);
            }
        }
    }


    public void sendToUser(String username, String payload) {

        Set<WebSocketSession> userSessions = sessions.get(username);

        if (userSessions == null) {
            return;
        }

        TextMessage message = new TextMessage(payload);

        for (WebSocketSession session : userSessions) {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                }
            } catch (IOException e) {
                log.debug("Dropping message to {} on a failed session: {}", username, e.getMessage());
            }
        }
    }
}
