package com.app.productmanagement.media.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface ObjectStorageClient {
    PresignedUpload createUploadUrl(String objectKey, String mimeType, Duration ttl);

    PresignedRead createReadUrl(String objectKey, Duration ttl);

    /** Rỗng khi object không tồn tại. Mã lỗi HTTP do tầng gọi quyết định. */
    Optional<ObjectInfo> getObjectInfo(String objectKey);

    /**
     * Đọc tối đa {@code maxBytes} byte đầu của object.
     *
     * <p>Đủ để lấy magic byte hoặc kích thước ảnh mà không kéo cả file về RAM:
     * MinIO chỉ gửi đúng khúc được yêu cầu. Trả mảng rỗng khi object không tồn
     * tại hoặc rỗng.
     */
    byte[] getObjectBytes(String objectKey, int maxBytes);

    void deleteObject(String objectKey);

    /** Thêm bởi luồng upload ảnh: dựng URL tĩnh từ endpoint + bucket + key. */
    String publicUrl(String objectKey);

    record PresignedUpload(String url, String method, Map<String, String> headers, Instant expiresAt) {
    }

    record PresignedRead(String url, Instant expiresAt) {
    }

    record ObjectInfo(long size, String eTag, String contentType) {
    }
}
