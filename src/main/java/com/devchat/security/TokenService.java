package com.devchat.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Component
public class TokenService {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long ttlSeconds;


    public TokenService(
            @Value("${devchat.auth.secret}") String secret,
            @Value("${devchat.auth.token-ttl-hours:720}") long ttlHours) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlHours * 3600;
    }


    public String issue(String username) {
        long now = Instant.now().getEpochSecond();
        long expiry = now + ttlSeconds;

        String payload = username + ":" + expiry;

        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String encodedPayload = B64.encodeToString(payloadBytes);

        return encodedPayload + "." + sign(encodedPayload);
    }


    public Optional<String> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }

        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }

        String encodedPayload = token.substring(0, dot);
        String signature = token.substring(dot + 1);

        String expectedSignature = sign(encodedPayload);
        if (!constantTimeEquals(signature, expectedSignature)) {
            return Optional.empty();
        }

        String payload;
        try {
            byte[] decoded = B64D.decode(encodedPayload);
            payload = new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        int sep = payload.lastIndexOf(':');
        if (sep < 0) {
            return Optional.empty();
        }

        long expiry;
        try {
            String expiryPart = payload.substring(sep + 1);
            expiry = Long.parseLong(expiryPart);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        long now = Instant.now().getEpochSecond();
        if (now >= expiry) {
            return Optional.empty();
        }

        String username = payload.substring(0, sep);
        return Optional.of(username);
    }


    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
            return B64.encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign token", e);
        }
    }


    private static boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
