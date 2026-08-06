package com.app.user.controller;

import com.app.user.dto.LoginRequest;
import com.app.user.dto.RefreshRequest;
import com.app.user.dto.RegisterRequest;
import com.app.user.dto.TokenResponse;
import com.app.user.service.AuthService;
import com.app.user.service.RegisterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RegisterService registerService;

    @PostMapping("/register")
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return registerService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }
}
