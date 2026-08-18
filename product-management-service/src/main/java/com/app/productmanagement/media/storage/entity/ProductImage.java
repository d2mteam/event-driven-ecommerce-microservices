package com.app.productmanagement.media.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "product_images",
        indexes = @Index(
                name = "idx_product_images_product",
                columnList = "product_id"
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImage {
    /**
     * Đường dẫn trong MinIO, đồng thời là khoá chính — không cần thêm id
     * riêng vì hai thứ đó luôn là một.
     */
    @Id
    @Column(name = "object_key", length = 200)
    private String objectKey;

    /**
     * Dòng chỉ tồn tại khi ảnh đã thuộc về một sản phẩm, nên cột này không bao
     * giờ null. Ảnh tải lên mà chưa gắn thì không có dòng nào cả — nó chỉ nằm
     * trong MinIO, và bị dọn bằng cách đối chiếu bucket với bảng này.
     */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Đọc từ MinIO lúc gắn, không tin lời client khai. */
    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    /** Thời điểm ảnh được gắn vào sản phẩm. Chỉ để tra cứu. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
