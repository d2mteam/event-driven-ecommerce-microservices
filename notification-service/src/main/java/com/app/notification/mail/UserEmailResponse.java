package com.app.notification.mail;

import java.util.UUID;

/** Bản sao DTO của user-service, đúng quy ước dùng chung trong project này:
 *  mỗi service tự giữ bản sao hình dạng response nó gọi tới. */
public record UserEmailResponse(UUID id, String email) {
}
