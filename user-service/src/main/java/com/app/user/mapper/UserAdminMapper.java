package com.app.user.mapper;

import com.app.user.dto.AdminUserResponse;
import com.app.user.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserAdminMapper {

    AdminUserResponse toResponse(User user);
}
