package com.devchat.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(String conversationId, String from, String text, Instant timestamp,
                          String kind, String fileName, String fileId) {

    public static ChatMessage text(String conversationId, String from, String text, Instant timestamp) {
        return new ChatMessage(conversationId, from, text, timestamp, Message.KIND_TEXT, null, null);
    }

    public static ChatMessage file(String conversationId, String from, String fileName, String fileId,
                                   Instant timestamp) {
        return new ChatMessage(conversationId, from, fileName, timestamp, Message.KIND_FILE, fileName, fileId);
    }
}
