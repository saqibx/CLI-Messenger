package com.devchat.service;

import com.devchat.model.Contact;
import com.devchat.model.User;
import com.devchat.repo.ContactRepository;
import com.devchat.repo.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ContactService {

    private final ContactRepository contacts;
    private final UserRepository users;


    public ContactService(ContactRepository contacts, UserRepository users) {
        this.contacts = contacts;
        this.users = users;
    }


    public User add(String owner, String usernameOrEmail) {
        String lookup = usernameOrEmail.trim().toLowerCase();

        Optional<User> found = users.findByUsernameOrEmail(lookup);
        if (found.isEmpty()) {
            throw new ContactException("No developer found for '" + usernameOrEmail + "'");
        }
        User target = found.get();

        if (target.getUsername().equals(owner)) {
            throw new ContactException("You can't add yourself as a contact.");
        }

        Contact contact = new Contact();
        contact.setOwner(owner);
        contact.setContact(target.getUsername());
        contact.setContactEmail(target.getEmail());

        long now = Instant.now().toEpochMilli();
        contact.setAddedAt(now);

        contacts.save(contact);
        return target;
    }


    public List<Contact> list(String owner) {
        return contacts.listForOwner(owner);
    }


    public static class ContactException extends RuntimeException {
        public ContactException(String message) {
            super(message);
        }
    }
}
