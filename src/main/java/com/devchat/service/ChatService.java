package com.devchat.service;

import com.devchat.kafka.MessagePublisher;
import com.devchat.model.ChatMessage;
import com.devchat.model.Conversation;
import com.devchat.model.ConversationMember;
import com.devchat.model.Message;
import com.devchat.model.StoredFile;
import com.devchat.model.User;
import com.devchat.repo.ConversationRepository;
import com.devchat.repo.FileRepository;
import com.devchat.repo.MessageRepository;
import com.devchat.repo.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ChatService {

    public static final int MAX_FILE_BYTES = 256 * 1024;

    private final UserRepository users;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final FileRepository files;
    private final MessagePublisher publisher;


    public ChatService(UserRepository users, ConversationRepository conversations,
                       MessageRepository messages, FileRepository files, MessagePublisher publisher) {
        this.users = users;
        this.conversations = conversations;
        this.messages = messages;
        this.files = files;
        this.publisher = publisher;
    }


    public ConversationView openDirect(String owner, String otherIdentifier) {
        String lookup = otherIdentifier.trim().toLowerCase();

        Optional<User> otherFound = users.findByUsernameOrEmail(lookup);
        if (otherFound.isEmpty()) {
            throw new ChatException("No developer found for '" + otherIdentifier + "'");
        }
        User other = otherFound.get();

        if (other.getUsername().equals(owner)) {
            throw new ChatException("You can't start a DM with yourself.");
        }

        User me = users.findByUsername(owner).orElseThrow();
        String id = directId(owner, other.getUsername());

        if (conversations.findConversation(id).isEmpty()) {
            Conversation conv = new Conversation();
            conv.setId(id);
            conv.setType(Conversation.DIRECT);
            conv.setCreatedBy(owner);
            conv.setCreatedAt(Instant.now().toEpochMilli());
            conv.setMembers(List.of(owner, other.getUsername()));
            conversations.saveConversation(conv);

            addMember(id, owner, other.getDisplayName(), Conversation.DIRECT);
            addMember(id, other.getUsername(), me.getDisplayName(), Conversation.DIRECT);
        }

        return new ConversationView(id, other.getDisplayName(), Conversation.DIRECT);
    }


    public ConversationView createGroup(String owner, String groupName, List<String> memberIdentifiers) {
        if (groupName == null || groupName.isBlank()) {
            throw new ChatException("Group name is required.");
        }

        Set<String> memberUsernames = new LinkedHashSet<>();
        memberUsernames.add(owner);

        for (String identifier : memberIdentifiers) {
            String lookup = identifier.trim().toLowerCase();

            Optional<User> found = users.findByUsernameOrEmail(lookup);
            if (found.isEmpty()) {
                throw new ChatException("No developer found for '" + identifier + "'");
            }
            User u = found.get();
            memberUsernames.add(u.getUsername());
        }

        if (memberUsernames.size() < 2) {
            throw new ChatException("A group needs at least one other member.");
        }

        String id = "grp#" + UUID.randomUUID();
        String trimmedName = groupName.trim();

        Conversation conv = new Conversation();
        conv.setId(id);
        conv.setType(Conversation.GROUP);
        conv.setName(trimmedName);
        conv.setCreatedBy(owner);
        conv.setCreatedAt(Instant.now().toEpochMilli());
        conv.setMembers(new ArrayList<>(memberUsernames));
        conversations.saveConversation(conv);

        for (String username : memberUsernames) {
            addMember(id, username, trimmedName, Conversation.GROUP);
        }

        return new ConversationView(id, trimmedName, Conversation.GROUP);
    }


    public void addToGroup(String requester, String conversationId, String newMemberIdentifier) {
        Conversation conv = conversations.findConversation(conversationId)
                .orElseThrow(() -> new ChatException("Conversation not found."));

        if (!Conversation.GROUP.equals(conv.getType())) {
            throw new ChatException("You can only add people to group chats.");
        }

        if (!conv.getMembers().contains(requester)) {
            throw new ChatException("You're not a member of that group.");
        }

        String lookup = newMemberIdentifier.trim().toLowerCase();

        Optional<User> found = users.findByUsernameOrEmail(lookup);
        if (found.isEmpty()) {
            throw new ChatException("No developer found for '" + newMemberIdentifier + "'");
        }
        User newMember = found.get();

        if (conv.getMembers().contains(newMember.getUsername())) {
            throw new ChatException(newMember.getUsername() + " is already in the group.");
        }

        List<String> updated = new ArrayList<>(conv.getMembers());
        updated.add(newMember.getUsername());
        conv.setMembers(updated);
        conversations.saveConversation(conv);

        addMember(conversationId, newMember.getUsername(), conv.getName(), Conversation.GROUP);
    }


    public void send(String sender, String conversationId, String text) {
        Conversation conv = conversations.findConversation(conversationId)
                .orElseThrow(() -> new ChatException("Conversation not found."));

        if (!conv.getMembers().contains(sender)) {
            throw new ChatException("You're not a member of this conversation.");
        }

        if (text == null || text.isBlank()) {
            throw new ChatException("Message is empty.");
        }

        long ts = Instant.now().toEpochMilli();

        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setSortKey(Message.sortKeyFor(ts, UUID.randomUUID().toString()));
        msg.setFrom(sender);
        msg.setText(text);
        msg.setTimestamp(ts);
        msg.setKind(Message.KIND_TEXT);
        messages.save(msg);

        conversations.touchLastMessage(conversationId, conv.getMembers(), ts);
        publisher.publish(ChatMessage.text(conversationId, sender, text, Instant.ofEpochMilli(ts)));
    }


    public String sendFile(String sender, String conversationId, String fileName, String contentBase64) {
        Conversation conv = conversations.findConversation(conversationId)
                .orElseThrow(() -> new ChatException("Conversation not found."));

        if (!conv.getMembers().contains(sender)) {
            throw new ChatException("You're not a member of this conversation.");
        }

        if (fileName == null || fileName.isBlank()) {
            throw new ChatException("File name is required.");
        }

        if (contentBase64 == null || contentBase64.isBlank()) {
            throw new ChatException("File is empty.");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(contentBase64);
        } catch (IllegalArgumentException e) {
            throw new ChatException("File content is not valid.");
        }

        if (bytes.length == 0) {
            throw new ChatException("File is empty.");
        }

        if (bytes.length > MAX_FILE_BYTES) {
            throw new ChatException("File is too large (" + (bytes.length / 1024) + " KB). "
                    + "Max is " + (MAX_FILE_BYTES / 1024) + " KB for now.");
        }

        long ts = Instant.now().toEpochMilli();
        String fileId = "file#" + UUID.randomUUID();

        StoredFile stored = new StoredFile();
        stored.setId(fileId);
        stored.setConversationId(conversationId);
        stored.setName(fileName);
        stored.setContentBase64(contentBase64);
        stored.setSize(bytes.length);
        stored.setFrom(sender);
        stored.setCreatedAt(ts);
        files.save(stored);

        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setSortKey(Message.sortKeyFor(ts, UUID.randomUUID().toString()));
        msg.setFrom(sender);
        msg.setText(fileName);
        msg.setTimestamp(ts);
        msg.setKind(Message.KIND_FILE);
        msg.setFileId(fileId);
        messages.save(msg);

        conversations.touchLastMessage(conversationId, conv.getMembers(), ts);
        publisher.publish(ChatMessage.file(conversationId, sender, fileName, fileId, Instant.ofEpochMilli(ts)));
        return fileId;
    }


    public StoredFile getFile(String username, String fileId) {
        StoredFile file = files.findById(fileId)
                .orElseThrow(() -> new ChatException("File not found."));

        if (!conversations.isMember(username, file.getConversationId())) {
            throw new ChatException("You don't have access to that file.");
        }

        return file;
    }


    public List<ConversationMember> listChats(String username) {
        List<ConversationMember> chats = new ArrayList<>(conversations.listMemberships(username));
        chats.sort(Comparator.comparingLong(ConversationMember::getLastMessageAt).reversed());
        return chats;
    }


    public List<Message> history(String username, String conversationId, int limit) {
        if (!conversations.isMember(username, conversationId)) {
            throw new ChatException("You're not a member of this conversation.");
        }
        return messages.recent(conversationId, limit);
    }


    public ConversationView open(String username, String conversationId) {
        ConversationMember member = conversations.findMember(username, conversationId)
                .orElseThrow(() -> new ChatException("You're not a member of that conversation (or it doesn't exist)."));
        return new ConversationView(member.getConversationId(), member.getConversationName(), member.getType());
    }


    public String labelFor(String username, String conversationId) {
        Optional<ConversationMember> member = conversations.findMember(username, conversationId);
        if (member.isPresent()) {
            return member.get().getConversationName();
        }
        return conversationId;
    }


    private void addMember(String conversationId, String username, String label, String type) {
        ConversationMember member = new ConversationMember();
        member.setUsername(username);
        member.setConversationId(conversationId);
        member.setConversationName(label);
        member.setType(type);
        member.setLastMessageAt(Instant.now().toEpochMilli());
        conversations.saveMember(member);
    }


    // dont touch this
    static String directId(String a, String b) {
        if (a.compareTo(b) <= 0) {
            return "dm#" + a + "#" + b;
        } else {
            return "dm#" + b + "#" + a;
        }
    }


    public static class ChatException extends RuntimeException {
        public ChatException(String message) {
            super(message);
        }
    }
}
