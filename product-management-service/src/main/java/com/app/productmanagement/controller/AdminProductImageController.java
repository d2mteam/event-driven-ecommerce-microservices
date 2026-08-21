package com.app.productmanagement.controller;

import com.app.productmanagement.media.storage.dto.CreateUploadsRequest;
import com.app.productmanagement.media.storage.dto.UploadTicket;
import com.app.productmanagement.media.storage.service.ProductImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cấp chỗ cho admin tự tải ảnh lên MinIO. Byte không đi qua service.
 *
 * <p>Không ghi database: {@code objectKey} trả về ở đây chỉ trở thành dòng khi
 * client gửi lại nó trong request tạo hoặc sửa sản phẩm.
 *
 * <p>Gateway đã chặn {@code /api/admin/**} bằng hasRole("ADMIN").
 */
@RestController
@RequestMapping("/api/admin/product-images")
@RequiredArgsConstructor
public class AdminProductImageController {

    private final ProductImageService productImageService;

    @PostMapping
    public List<UploadTicket> createUploads(@Valid @RequestBody CreateUploadsRequest request) {
        return productImageService.createUploads(request);
    }
}
