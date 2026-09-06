package com.devchat.web;

import com.devchat.security.TokenService;
import org.springframework.stereotype.Component;

@Component
public class AuthSupport {

    private final TokenService tokens;

    public AuthSupport(TokenService tokens) {
        this.tokens = tokens;
    }


    public String requireUser(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Not logged in. Run: msg login");
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();

        return tokens.verify(token)
                .orElseThrow(() -> new UnauthorizedException("Your session has expired. Run: msg login"));
    }
}
