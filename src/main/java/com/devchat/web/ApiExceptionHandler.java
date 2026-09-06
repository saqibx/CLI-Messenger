package com.devchat.web;

import com.devchat.service.AuthService;
import com.devchat.service.ChatService;
import com.devchat.service.ContactService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({AuthService.AuthException.class, ChatService.ChatException.class,
            ContactService.ContactException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException e) {
        Map<String, String> body = Map.of("error", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }


    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(UnauthorizedException e) {
        Map<String, String> body = Map.of("error", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
}
