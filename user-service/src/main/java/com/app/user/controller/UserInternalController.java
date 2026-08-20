package com.app.user.controller;

import com.app.user.dto.UserEmailResponse;
import com.app.user.repository.UserRepository;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Gọi trực tiếp service-tới-service, không qua gateway — như các /internal/** khác. */
@RestController
@RequiredArgsConstructor
@Validated
public class UserInternalController {

    private final UserRepository userRepository;

    @PostMapping("/internal/users/batch")
    public List<UserEmailResponse> findAll(
            @RequestBody @Size(max = 50) Set<UUID> userIds
    ) {
        return userRepository.findAllByIdIn(userIds).stream()
                .map(user -> new UserEmailResponse(user.getId(), user.getEmail()))
                .toList();
    }
}
