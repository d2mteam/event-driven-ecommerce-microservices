package com.app.productmanagement.media.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class S3ObjectStorageClient implements ObjectStorageClient {

    /**
     * Range không thoả mãn được — object rỗng.
     */
    private static final int STATUS_RANGE_NOT_SATISFIABLE = 416;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String endpoint;

    S3ObjectStorageClient(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${app.storage.bucket}") String bucket,
            @Value("${app.storage.endpoint}") String endpoint
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        // Cắt dấu / cuối một lần ở đây, để publicUrl khỏi sinh ra "//".
        this.endpoint = endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1)
                : endpoint;
    }

    /**
     * Sai tên bucket thì hỏng lặng lẽ — headObject trả NoSuchKey chứ không trả
     * NoSuchBucket, nên mọi lần tra đều ra rỗng mà không ai biết vì sao.
     * Chỉ kiểm, không tạo: tạo bucket là việc của container minio-init.
     */
    @PostConstruct
    void verifyBucket() {
        try {
            s3Client.headBucket(builder -> builder.bucket(bucket));
        } catch (NoSuchBucketException exception) {
            throw new IllegalStateException(
                    "Bucket '%s' does not exist. Start the minio-init container first."
                            .formatted(bucket),
                    exception
            );
        }
    }

    @Override
    public PresignedUpload createUploadUrl(String objectKey, String mimeType, Duration ttl) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(mimeType)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .putObjectRequest(request)
                        .build()
        );
        return new PresignedUpload(
                presigned.url().toString(),
                presigned.httpRequest().method().name(),
                signedHeadersFor(presigned),
                presigned.expiration()
        );
    }

    /**
     * Những header client bắt buộc gửi kèm khi PUT — thiếu hoặc sai một cái là
     * chữ ký không khớp và MinIO trả 403.
     *
     * <p>Lấy thẳng từ SDK chứ không tự liệt kê: thêm tuỳ chọn nào vào
     * {@link PutObjectRequest} (cacheControl, metadata, contentMD5...) thì
     * header tương ứng tự xuất hiện ở đây, không phải sửa chỗ này.
     *
     * <p>Bỏ {@code host} vì trình duyệt tự đặt, JS không set được.
     */
    private static Map<String, String> signedHeadersFor(PresignedPutObjectRequest presigned) {
        Map<String, String> headers = new LinkedHashMap<>();
        presigned.signedHeaders().forEach((name, values) -> {
            if (!name.equalsIgnoreCase("host")) {
                // Một header HTTP có thể xuất hiện nhiều lần nên SDK trả về List.
                // Nối bằng dấu phẩy — đúng cách HTTP gộp các dòng cùng tên.
                headers.put(name, String.join(",", values));
            }
        });
        return headers;
    }

    @Override
    public PresignedRead createReadUrl(String objectKey, Duration ttl) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(request)
                        .build()
        );
        return new PresignedRead(presigned.url().toString(), presigned.expiration());
    }

    @Override
    public Optional<ObjectInfo> getObjectInfo(String objectKey) {
        try {
            HeadObjectResponse head = s3Client.headObject(builder -> builder
                    .bucket(bucket)
                    .key(objectKey));
            return Optional.of(new ObjectInfo(head.contentLength(), head.eTag(), head.contentType()));
        } catch (NoSuchKeyException exception) {
            // HEAD không có body nên SDK cũng ném đúng exception này khi sai tên
            // bucket. verifyBucket đã loại khả năng đó lúc khởi động.
            return Optional.empty();
        }
    }

    @Override
    public byte[] getObjectBytes(String objectKey, int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be greater than 0");
        }
        try {
            // Range tính cả hai đầu: bytes=0-99 trả về đúng 100 byte.
            // Object nhỏ hơn khoảng yêu cầu thì S3 trả phần có thật, không lỗi.
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(builder -> builder
                    .bucket(bucket)
                    .key(objectKey)
                    .range("bytes=0-" + (maxBytes - 1)));
            return response.asByteArray();
        } catch (NoSuchKeyException exception) {
            return new byte[0];
        } catch (S3Exception exception) {
            if (exception.statusCode() == STATUS_RANGE_NOT_SATISFIABLE) {
                return new byte[0];
            }
            // 403 sai khoá, 500 MinIO lỗi — nuốt đi thì trông y hệt file rỗng.
            throw exception;
        }
    }

    /**
     * DELETE trong S3 là idempotent: xoá key không tồn tại vẫn trả 204.
     */
    @Override
    public void deleteObject(String objectKey) {
        s3Client.deleteObject(builder -> builder
                .bucket(bucket)
                .key(objectKey));
    }

    @Override
    public String publicUrl(String objectKey) {
        return endpoint + "/" + bucket + "/" + objectKey;
    }
}
