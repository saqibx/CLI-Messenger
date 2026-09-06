package com.devchat.service;

import com.devchat.model.User;
import com.devchat.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_.-]{3,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository users;
    private final PasswordEncoder encoder;


    public AuthService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }


    public User createAccount(String username, String email, String password, String displayName) {
        username = normalize(username);
        email = normalize(email);
        validate(username, email, password);

        if (users.findByEmail(email).isPresent()) {
            throw new AuthException("An account already uses that email: " + email);
        }

        String finalDisplayName;
        if (displayName == null || displayName.isBlank()) {
            finalDisplayName = username;
        } else {
            finalDisplayName = displayName.trim();
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(finalDisplayName);
        user.setPasswordHash(encoder.encode(password));

        long now = Instant.now().toEpochMilli();
        user.setCreatedAt(now);

        try {
            users.createNew(user);
        } catch (UserRepository.UsernameTakenException e) {
            throw new AuthException("That username is already taken: " + username);
        }

        return user;
    }


    public User authenticate(String usernameOrEmail, String password) {
        User user = users.findByUsernameOrEmail(normalize(usernameOrEmail))
                .orElseThrow(() -> new AuthException("No account found for '" + usernameOrEmail + "'"));

        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new AuthException("Incorrect password.");
        }

        return user;
    }


    private void validate(String username, String email, String password) {
        if (!USERNAME.matcher(username).matches()) {
            throw new AuthException("Username must be 3-30 chars: letters, digits, and . _ - only.");
        }

        if (!EMAIL.matcher(email).matches()) {
            throw new AuthException("That doesn't look like a valid email address.");
        }

        if (password == null || password.length() < 8) {
            throw new AuthException("Password must be at least 8 characters.");
        }
    }


    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toLowerCase();
    }


    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }
}
