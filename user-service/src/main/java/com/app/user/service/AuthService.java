package com.app.user.service;

import com.app.user.dto.LoginRequest;
import com.app.user.dto.RefreshRequest;
import com.app.user.dto.TokenResponse;
import com.app.user.entity.User;
import com.app.user.exception.InvalidCredentialsException;
import com.app.user.exception.InvalidRefreshTokenException;
import com.app.user.exception.JwtException;
import com.app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public TokenResponse login(LoginRequest loginRequest) {
        User user = userRepository.findByUsername(loginRequest.username())
                // Cùng một thông báo cho "không có user" và "sai mật khẩu" — tránh lộ
                // việc username có tồn tại hay không (username enumeration).
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(loginRequest.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        List<String> roles = List.of(user.getRole().toString());
        return new TokenResponse(
                jwtService.generateAccessToken(user.getId(), roles),
                jwtService.generateRefreshToken(user.getId())
        );
    }

    public TokenResponse refresh(RefreshRequest refreshRequest) {
        JwtService.DecodedToken decoded;
        try {
            decoded = jwtService.decodeAndValidate(
                    refreshRequest.refreshToken(),
                    JwtService.REFRESH_TOKEN_TYPE
            );
        } catch (JwtException exception) {
            throw new InvalidRefreshTokenException("Refresh token is invalid or expired", exception);
        }

        User user = userRepository.findById(decoded.userId())
                .orElseThrow(() -> new InvalidRefreshTokenException("User no longer exists"));

        // Chỉ phát access token mới; refresh token cũ giữ nguyên, không xoay vòng.
        // TODO: không có cách thu hồi refresh token trước hạn (đổi mật khẩu, đăng
        // xuất toàn bộ thiết bị) — cần bảng lưu trạng thái nếu làm thật.
        List<String> roles = List.of(user.getRole().toString());
        return new TokenResponse(
                jwtService.generateAccessToken(user.getId(), roles),
                refreshRequest.refreshToken()
        );
    }
}
