package com.app.productmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class StorageConfig {

    @Bean
    S3Client s3Client(StorageProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.US_EAST_1)          // MinIO không dùng, nhưng SDK bắt buộc có
                .credentialsProvider(credentials(props))
                .forcePathStyle(true)              // MinIO không phân giải bucket.minio:9000
                .build();
    }

    /** URL ký ra là để trình duyệt gọi, nên ký bằng địa chỉ công khai. */
    @Bean
    S3Presigner s3Presigner(StorageProperties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.getPublicEndpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials(props))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private static StaticCredentialsProvider credentials(StorageProperties props) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey()));
    }
}
