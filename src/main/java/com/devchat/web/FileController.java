package com.devchat.web;

import com.devchat.model.StoredFile;
import com.devchat.service.ChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final ChatService chat;
    private final AuthSupport auth;

    public FileController(ChatService chat, AuthSupport auth) {
        this.chat = chat;
        this.auth = auth;
    }

    public record FileDto(String name, String contentBase64, long size, String from) {}


    @GetMapping("/{fileId}")
    public FileDto get(@RequestHeader("Authorization") String token, @PathVariable String fileId) {
        String user = auth.requireUser(token);

        StoredFile file = chat.getFile(user, fileId);

        return new FileDto(file.getName(), file.getContentBase64(), file.getSize(), file.getFrom());
    }
}
