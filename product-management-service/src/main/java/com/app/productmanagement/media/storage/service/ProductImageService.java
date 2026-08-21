package com.app.productmanagement.media.storage.service;

import com.app.productmanagement.config.StorageProperties;
import com.app.productmanagement.exception.ProductImageStateConflictException;
import com.app.productmanagement.media.storage.ObjectStorageClient;
import com.app.productmanagement.media.storage.dto.CreateUploadsRequest;
import com.app.productmanagement.media.storage.dto.UploadTicket;
import com.app.productmanagement.media.storage.entity.ProductImage;
import com.app.productmanagement.media.storage.repository.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductImageService {

    private static final String KEY_PREFIX = "products/";
    private static final int MAX_IMAGES_PER_PRODUCT = 8;

    private final ProductImageRepository productImageRepository;
    private final ObjectStorageClient objectStorageClient;
    private final StorageProperties storageProperties;


    public List<UploadTicket> createUploads(CreateUploadsRequest request) {
        List<UploadTicket> tickets = new ArrayList<>();
        for (String mimeType : request.mimeTypes()) {
            String objectKey = KEY_PREFIX + UUID.randomUUID();
            ObjectStorageClient.PresignedUpload upload = objectStorageClient.createUploadUrl(
                    objectKey,
                    mimeType,
                    storageProperties.getPresignTtl()
            );
            tickets.add(UploadTicket.builder()
                    .objectKey(objectKey)
                    .uploadUrl(upload.url())
                    .headers(upload.headers())
                    .publicUrl(objectStorageClient.publicUrl(objectKey))
                    .build());
        }
        return tickets;
    }


    @Transactional
    public void attachTo(Long productId, List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }

        List<String> keys = objectKeys.stream().distinct().toList();
        if (keys.size() > MAX_IMAGES_PER_PRODUCT) {
            throw new ProductImageStateConflictException(
                    "A product can have at most %d images".formatted(MAX_IMAGES_PER_PRODUCT));
        }
        // Một câu query cho cả lô: key nào đã có dòng nghĩa là đã thuộc sản
        // phẩm khác, không cho gắn tiếp — xoá sản phẩm này sẽ làm ảnh của sản
        // phẩm kia biến mất.
        if (productImageRepository.existsByObjectKeyIn(keys)) {
            throw new ProductImageStateConflictException(
                    "One of the images is already attached to a product");
        }

        List<ProductImage> images = new ArrayList<>();
        for (int position = 0; position < keys.size(); position++) {
            String objectKey = keys.get(position);
            ObjectStorageClient.ObjectInfo info = objectStorageClient.getObjectInfo(objectKey)
                    .orElseThrow(() -> new ProductImageStateConflictException(
                            "Image was never uploaded: " + objectKey));
            images.add(ProductImage.builder()
                    .objectKey(objectKey)
                    .productId(productId)
                    .sortOrder(position)
                    .mimeType(info.contentType())
                    .build());
        }
        productImageRepository.saveAll(images);
    }

    /**
     * Đặt lại toàn bộ danh sách ảnh của một sản phẩm theo đúng thứ tự gửi lên.
     *
     * <p>Ảnh bị gỡ chỉ xoá dòng, không xoá file — xoá file trong transaction mà
     * transaction rollback thì mất ảnh vĩnh viễn. Object mồ côi để đợt đối
     * chiếu bucket dọn sau.
     *
     * <p>Danh sách không đổi thì không gọi MinIO lần nào.
     */
    @Transactional
    public void replaceFor(Long productId, List<String> objectKeys) {
        List<String> desired = objectKeys == null
                ? List.of()
                : objectKeys.stream().distinct().toList();

        List<ProductImage> current = productImageRepository
                .findAllByProductIdOrderBySortOrder(productId);
        List<String> currentKeys = current.stream().map(ProductImage::getObjectKey).toList();

        if (currentKeys.equals(desired)) {
            return;
        }
        productImageRepository.deleteAll(current);
        // Ép DELETE xuống database trước khi attachTo chèn lại.
        //
        // Trong một lần flush, Hibernate chạy INSERT trước DELETE. Ảnh giữ
        // nguyên khi đảo thứ tự sẽ bị chèn lại với đúng khoá chính cũ trong
        // khi bản cũ chưa xoá -> trùng khoá.
        //
        // Hiện câu exists trong attachTo cũng vô tình kích hoạt auto-flush và
        // che mất chuyện này. Gọi thẳng ở đây để thứ tự là chủ ý, không phụ
        // thuộc việc có query nào xen vào giữa hay không.
        productImageRepository.flush();
        attachTo(productId, desired);
    }

    /** Cho GET /api/products/{id}. */
    @Transactional(readOnly = true)
    public List<String> urlsOf(Long productId) {
        return productImageRepository.findAllByProductIdOrderBySortOrder(productId).stream()
                .map(image -> objectStorageClient.publicUrl(image.getObjectKey()))
                .toList();
    }

    /** Cho trang danh mục — một câu query cho cả trang, tránh N+1. */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> urlsByProductId(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> urlsByProductId = new LinkedHashMap<>();
        for (ProductImage image : productImageRepository.findAllByProductIdInOrderBySortOrder(productIds)) {
            urlsByProductId
                    .computeIfAbsent(image.getProductId(), key -> new ArrayList<>())
                    .add(objectStorageClient.publicUrl(image.getObjectKey()));
        }
        return urlsByProductId;
    }
}
