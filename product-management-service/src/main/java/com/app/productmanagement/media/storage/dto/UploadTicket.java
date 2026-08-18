package com.app.productmanagement.media.storage.dto;

import lombok.Builder;

import java.util.Map;

/**
 * @param objectKey thứ client gửi lại trong request tạo sản phẩm
 * @param uploadUrl client PUT thẳng vào đây, không qua server
 * @param headers   bắt buộc gửi đúng, thiếu một cái là MinIO trả 403
 * @param publicUrl chỉ để kiểm tay lúc phát triển — bỏ khi làm thật, vì
 *                  object này còn chưa thuộc sản phẩm nào
 */
@Builder
public record UploadTicket(
        String objectKey,
        String uploadUrl,
        Map<String, String> headers,
        String publicUrl
) {
}
