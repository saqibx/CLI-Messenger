package com.devchat.web;

import com.devchat.model.Contact;
import com.devchat.model.User;
import com.devchat.service.ContactService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contacts;
    private final AuthSupport auth;

    public ContactController(ContactService contacts, AuthSupport auth) {
        this.contacts = contacts;
        this.auth = auth;
    }

    public record AddContactRequest(String usernameOrEmail) {}

    public record ContactDto(String username, String email) {
        static ContactDto of(Contact c) {
            return new ContactDto(c.getContact(), c.getContactEmail());
        }
    }

    public record AddedContactDto(String username, String displayName, String email) {}


    @PostMapping
    public AddedContactDto add(@RequestHeader("Authorization") String token,
                               @RequestBody AddContactRequest req) {
        String user = auth.requireUser(token);

        User added = contacts.add(user, req.usernameOrEmail());

        return new AddedContactDto(added.getUsername(), added.getDisplayName(), added.getEmail());
    }


    @GetMapping
    public List<ContactDto> list(@RequestHeader("Authorization") String token) {
        String user = auth.requireUser(token);

        List<Contact> found = contacts.list(user);

        List<ContactDto> result = new ArrayList<>();
        for (Contact c : found) {
            result.add(ContactDto.of(c));
        }
        return result;
    }
}
