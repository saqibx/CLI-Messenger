package com.devchat.web;

import com.devchat.model.ConversationMember;
import com.devchat.model.Message;
import com.devchat.service.ChatService;
import com.devchat.service.ConversationView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService chat;
    private final AuthSupport auth;

    public ChatController(ChatService chat, AuthSupport auth) {
        this.chat = chat;
        this.auth = auth;
    }

    public record OpenDirectRequest(String usernameOrEmail) {}

    public record CreateGroupRequest(String name, List<String> members) {}

    public record AddMemberRequest(String usernameOrEmail) {}

    public record SendRequest(String text) {}

    public record UploadFileRequest(String name, String contentBase64) {}

    public record UploadFileResponse(String fileId, String name) {}

    public record MessageDto(String from, String text, long timestamp, String kind, String fileId) {
        static MessageDto of(Message m) {
            String kind;
            if (m.getKind() == null) {
                kind = Message.KIND_TEXT;
            } else {
                kind = m.getKind();
            }
            return new MessageDto(m.getFrom(), m.getText(), m.getTimestamp(), kind, m.getFileId());
        }
    }

    public record ChatSummary(String id, String name, String type, long lastMessageAt) {
        static ChatSummary of(ConversationMember m) {
            return new ChatSummary(m.getConversationId(), m.getConversationName(), m.getType(), m.getLastMessageAt());
        }
    }


    @PostMapping("/direct")
    public ConversationView openDirect(@RequestHeader("Authorization") String token,
                                       @RequestBody OpenDirectRequest req) {
        String user = auth.requireUser(token);
        return chat.openDirect(user, req.usernameOrEmail());
    }


    @PostMapping("/group")
    public ConversationView createGroup(@RequestHeader("Authorization") String token,
                                        @RequestBody CreateGroupRequest req) {
        String user = auth.requireUser(token);

        List<String> members;
        if (req.members() == null) {
            members = List.of();
        } else {
            members = req.members();
        }

        return chat.createGroup(user, req.name(), members);
    }


    @PostMapping("/{id}/members")
    public ConversationView addMember(@RequestHeader("Authorization") String token,
                                      @PathVariable String id,
                                      @RequestBody AddMemberRequest req) {
        String user = auth.requireUser(token);
        chat.addToGroup(user, id, req.usernameOrEmail());
        return chat.open(user, id);
    }


    @GetMapping
    public List<ChatSummary> listChats(@RequestHeader("Authorization") String token) {
        String user = auth.requireUser(token);

        List<ConversationMember> chats = chat.listChats(user);

        List<ChatSummary> result = new ArrayList<>();
        for (ConversationMember m : chats) {
            result.add(ChatSummary.of(m));
        }
        return result;
    }


    @GetMapping("/{id}")
    public ConversationView open(@RequestHeader("Authorization") String token, @PathVariable String id) {
        String user = auth.requireUser(token);
        return chat.open(user, id);
    }


    @GetMapping("/{id}/messages")
    public List<MessageDto> history(@RequestHeader("Authorization") String token,
                                    @PathVariable String id,
                                    @RequestParam(defaultValue = "50") int limit) {
        String user = auth.requireUser(token);

        List<Message> messages = chat.history(user, id, limit);

        List<MessageDto> result = new ArrayList<>();
        for (Message m : messages) {
            result.add(MessageDto.of(m));
        }
        return result;
    }


    @PostMapping("/{id}/messages")
    public MessageDto send(@RequestHeader("Authorization") String token,
                           @PathVariable String id,
                           @RequestBody SendRequest req) {
        String user = auth.requireUser(token);
        chat.send(user, id, req.text());
        return new MessageDto(user, req.text(), System.currentTimeMillis(), Message.KIND_TEXT, null);
    }


    @PostMapping("/{id}/files")
    public UploadFileResponse sendFile(@RequestHeader("Authorization") String token,
                                       @PathVariable String id,
                                       @RequestBody UploadFileRequest req) {
        String user = auth.requireUser(token);
        String fileId = chat.sendFile(user, id, req.name(), req.contentBase64());
        return new UploadFileResponse(fileId, req.name());
    }
}
