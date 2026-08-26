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
     * Địa chỉ service tự gọi MinIO: headObject, getObject, deleteObject.
     *
     * <p>Chạy trên host thì là {@code http://localhost:9010}; chạy trong compose
     * thì là {@code http://minio:9000}.
     */
    private String endpoint = "http://localhost:9010";

    /**
     * Địa chỉ trình duyệt dùng để gọi MinIO: URL ký sẵn và {@code publicUrl}.
     *
     * <p>Tách khỏi {@link #endpoint} vì hai bên nhìn MinIO bằng hai tên khác
     * nhau khi service nằm trong container: service gọi được {@code minio:9000}
     * còn trình duyệt thì không, và ngược lại với {@code localhost:9010}.
     *
     * <p>Ký bằng host công khai vẫn hợp lệ: SigV4 ký cả header Host, MinIO đối
     * chiếu chữ ký với Host mà trình duyệt gửi lên chứ không so với tên nội bộ
     * của chính nó.
     *
     * <p>Để trống thì lấy theo {@link #endpoint} — chạy trên host không cần đặt.
     */
    private String publicEndpoint;

    public String getPublicEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank()
                ? endpoint
                : publicEndpoint;
    }

    private String bucket = "product-images";

    private String accessKey = "minioadmin";

    private String secretKey = "minioadmin";

    /** Hạn của URL ký cho client tự upload. Đủ dài để tải xong một ảnh. */
    private Duration presignTtl = Duration.ofMinutes(5);
}
