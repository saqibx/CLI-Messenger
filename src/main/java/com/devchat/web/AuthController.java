package com.devchat.web;

import com.devchat.model.User;
import com.devchat.security.TokenService;
import com.devchat.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokens;
    private final AuthSupport auth;

    public AuthController(AuthService authService, TokenService tokens, AuthSupport auth) {
        this.authService = authService;
        this.tokens = tokens;
        this.auth = auth;
    }

    public record RegisterRequest(String username, String email, String password, String displayName) {}

    public record LoginRequest(String usernameOrEmail, String password) {}

    public record AuthResponse(String token, String username, String displayName, String email) {}

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}


    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest req) {
        User user = authService.createAccount(req.username(), req.email(), req.password(), req.displayName());

        AuthResponse body = response(user);
        return ResponseEntity.ok(body);
    }


    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req) {
        User user = authService.authenticate(req.usernameOrEmail(), req.password());

        AuthResponse body = response(user);
        return ResponseEntity.ok(body);
    }


    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@RequestHeader("Authorization") String token,
                                               @RequestBody ChangePasswordRequest req) {
        String username = auth.requireUser(token);
        authService.changePassword(username, req.currentPassword(), req.newPassword());
        return ResponseEntity.ok().build();
    }


    private AuthResponse response(User user) {
        String token = tokens.issue(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getDisplayName(), user.getEmail());
    }
}
