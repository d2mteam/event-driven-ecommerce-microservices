package com.app.user.service;

import com.app.user.dto.RegisterRequest;
import com.app.user.dto.TokenResponse;
import com.app.user.entity.User;
import com.app.user.exception.RegistrationConflictException;
import com.app.user.model.Role;
import com.app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisterService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public TokenResponse register(RegisterRequest request) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(request.email())
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(Role.ROLE_USER)
                .build();

        try {
            // saveAndFlush, không phải save: hàm này không có @Transactional bao
            // ngoài nên save() cũng chạy đồng bộ trong trường hợp hiện tại — nhưng
            // saveAndFlush ép đúng hành vi đó, không phụ thuộc ngầm vào việc caller
            // mãi mãi không có @Transactional.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new RegistrationConflictException("Email hoặc username đã được sử dụng");
        }

        List<String> roles = List.of(user.getRole().toString());
        return new TokenResponse(
                jwtService.generateAccessToken(user.getId(), roles),
                jwtService.generateRefreshToken(user.getId())
        );
    }
}
