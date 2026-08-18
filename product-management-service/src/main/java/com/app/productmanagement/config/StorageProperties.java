package com.app.productmanagement.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** Cấu hình MinIO cho ảnh sản phẩm. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /**
     * Endpoint dùng cho cả S3Client lẫn S3Presigner.
     *
     * <p>URL ký ra mang đúng host này, và client là trình duyệt sẽ gọi thẳng vào
     * đó — nên phải là địa chỉ trình duyệt phân giải được. Hiện các service chạy
     * trên host nên một giá trị là đủ. Nếu sau này đóng gói service vào compose,
     * phải tách làm hai: nội bộ {@code http://minio:9000} cho S3Client, công khai
     * {@code http://localhost:9010} cho presigner và {@code publicUrl}.
     */
    private String endpoint = "http://localhost:9010";

    private String bucket = "product-images";

    private String accessKey = "minioadmin";

    private String secretKey = "minioadmin";

    /** Hạn của URL ký cho client tự upload. Đủ dài để tải xong một ảnh. */
    private Duration presignTtl = Duration.ofMinutes(5);
}
