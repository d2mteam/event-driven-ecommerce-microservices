package com.app.user.service;

import com.app.user.entity.PasswordResetToken;
import com.app.user.entity.User;
import com.app.user.exception.InvalidPasswordResetTokenException;
import com.app.user.repository.PasswordResetTokenRepository;
import com.app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.password-reset.frontend-url}")
    private String frontendUrl;

    @Value("${app.password-reset.ttl}")
    private Duration tokenTtl;

    @Value("${app.password-reset.from}")
    private String sender;

    public void request(String email) {
        userRepository.findByEmailIgnoreCase(email.strip()).ifPresent(user -> {
            String token = newToken();
            tokenRepository.save(new PasswordResetToken(
                    user.getId(),
                    hash(token),
                    Instant.now().plus(tokenTtl)
            ));
            sendLink(user, token);
        });
    }

    @Transactional
    public void reset(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(hash(token))
                .filter(value -> value.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(InvalidPasswordResetTokenException::new);

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(InvalidPasswordResetTokenException::new);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        tokenRepository.delete(resetToken);
    }

    private void sendLink(User user, String token) {
        String link = UriComponentsBuilder.fromUriString(frontendUrl)
                .queryParam("token", token)
                .build()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(user.getEmail());
        message.setSubject("Mini Store - Đặt lại mật khẩu");
        message.setText("Xin chào " + user.getFullName()
                + ",\n\nMở liên kết sau để đặt lại mật khẩu:\n"
                + link
                + "\n\nLiên kết chỉ sử dụng được một lần và sẽ sớm hết hạn.");
        mailSender.send(message);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
