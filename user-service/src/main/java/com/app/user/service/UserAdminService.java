package com.app.user.service;

import com.app.user.dto.AdminUserResponse;
import com.app.user.dto.PageResponse;
import com.app.user.exception.UserNotFoundException;
import com.app.user.mapper.UserAdminMapper;
import com.app.user.model.UserStatus;
import com.app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final UserAdminMapper userAdminMapper;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> findAll(
            String query,
            UserStatus status,
            int page,
            int size
    ) {
        String normalizedQuery = query == null || query.isBlank()
                ? null
                : query.strip();
        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"))
        );
        return PageResponse.from(
                userRepository
                        .searchAdminUsers(normalizedQuery, status, pageable)
                        .map(userAdminMapper::toResponse)
        );
    }

    @Transactional
    public AdminUserResponse changeStatus(
            UUID userId,
            UserStatus status
    ) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getStatus() == status) {
            return userAdminMapper.toResponse(user);
        }

        user.changeStatus(status);
        return userAdminMapper.toResponse(user);
    }
}
