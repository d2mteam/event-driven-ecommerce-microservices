package com.app.user.config;

import com.app.user.entity.User;
import com.app.user.model.Role;
import com.app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tạo tài khoản ROLE_ADMIN mặc định lúc khởi động — không có endpoint nào tạo
 * được admin (register luôn gán ROLE_USER, cố ý). Idempotent: cứ thử insert,
 * lần chạy sau vỡ unique constraint thì bỏ qua, không cần query kiểm tra trước.
 *
 * TODO: mật khẩu mặc định là plaintext trong config, chỉ chấp nhận được cho demo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${security.app.default-admin-username}")
    private String adminUsername;

    @Value("${security.app.default-admin-password}")
    private String adminPassword;

    @Value("${security.app.default-admin-email}")
    private String adminEmail;

    @Override
    public void run(ApplicationArguments args) {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .username(adminUsername)
                .email(adminEmail)
                .fullName("Administrator")
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ROLE_ADMIN)
                .build();

        try {
            userRepository.saveAndFlush(admin);
            log.info("Default admin account created: {}", adminUsername);
        } catch (DataIntegrityViolationException exception) {
            log.debug("Default admin account already exists: {}", adminUsername);
        }
    }
}
