package com.app.user.controller;

import com.app.user.dto.AdminUserResponse;
import com.app.user.dto.PageResponse;
import com.app.user.dto.UpdateUserStatusRequest;
import com.app.user.model.UserStatus;
import com.app.user.service.UserAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    public PageResponse<AdminUserResponse> findAll(
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return userAdminService.findAll(query, status, page, size);
    }

    @PutMapping("/{userId}/status")
    public AdminUserResponse changeStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        return userAdminService.changeStatus(userId, request.status());
    }
}
