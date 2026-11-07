package com.fashionapp.infra;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * S3 호환 저장소 (storage.type=s3): AWS S3, Tencent COS 등. endpoint·region·path-style·키를 모두 설정값으로 받는다.
 * endpoint가 비어 있으면 AWS 기본 엔드포인트를 쓴다. 이미지 URL은 presigned URL(7일)로 내려준다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3CompatibleStorage implements ImageStorage {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;
    private final long maxBytes;

    public S3CompatibleStorage(@Value("${storage.s3.endpoint:}") String endpoint,
                               @Value("${storage.s3.region:}") String region,
                               @Value("${storage.s3.bucket:}") String bucket,
                               @Value("${storage.s3.access-key:}") String accessKey,
                               @Value("${storage.s3.secret-key:}") String secretKey,
                               @Value("${storage.s3.path-style:false}") boolean pathStyle,
                               @Value("${storage.max-bytes:10485760}") long maxBytes) {
        require("S3_REGION", region);
        require("S3_BUCKET", bucket);
        require("S3_ACCESS_KEY", accessKey);
        require("S3_SECRET_KEY", secretKey);
        this.bucket = bucket;
        this.maxBytes = maxBytes;

        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build();
        var clientBuilder = S3Client.builder()
                .region(Region.of(region)).credentialsProvider(credentials).serviceConfiguration(s3Config);
        var presignerBuilder = S3Presigner.builder()
                .region(Region.of(region)).credentialsProvider(credentials).serviceConfiguration(s3Config);
        if (!endpoint.isBlank()) {
            clientBuilder.endpointOverride(URI.create(endpoint));
            presignerBuilder.endpointOverride(URI.create(endpoint));
        }
        this.s3Client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
        log.info("image storage: S3-compatible bucket={} endpoint={} pathStyle={}",
                bucket, endpoint.isBlank() ? "(aws default)" : endpoint, pathStyle);
    }

    private static void require(String envName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("STORAGE_TYPE=s3 이면 " + envName + " 설정이 필요합니다.");
        }
    }

    @PreDestroy
    void close() {
        s3Client.close();
        presigner.close();
    }

    @Override
    public String store(MultipartFile file, String category, UUID ownerId) {
        byte[] bytes = ImageKeys.validate(file, maxBytes);
        return put(bytes, file.getContentType(), category, ownerId);
    }

    @Override
    public String storeBytes(byte[] bytes, String contentType, String category, UUID ownerId) {
        ImageKeys.validateBytes(bytes, contentType, maxBytes);
        return put(bytes, contentType, category, ownerId);
    }

    private String put(byte[] bytes, String contentType, String category, UUID ownerId) {
        String key = ImageKeys.newKey(category, ownerId, contentType);
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket).key(key).contentType(contentType).contentLength((long) bytes.length).build(),
                    RequestBody.fromBytes(bytes));
        } catch (RuntimeException e) {
            log.error("S3 upload failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
        return key;
    }

    @Override
    public String resolveUrl(String storedKey) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofDays(7))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(ImageKeys.toKey(storedKey)).build())
                .build()).url().toString();
    }

    @Override
    public void delete(String storedKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(ImageKeys.toKey(storedKey)).build());
    }

    @Override
    public int deleteByPrefix(String prefix) {
        int deleted = 0;
        String continuationToken = null;
        do {
            // 한 번에 최대 1000개씩 나열 → 같은 크기로 일괄 삭제 (DeleteObjects 한도도 1000개)
            ListObjectsV2Response page = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket).prefix(prefix).continuationToken(continuationToken).build());
            List<ObjectIdentifier> keys = page.contents().stream()
                    .map(o -> ObjectIdentifier.builder().key(o.key()).build()).toList();
            if (!keys.isEmpty()) {
                s3Client.deleteObjects(DeleteObjectsRequest.builder().bucket(bucket)
                        .delete(Delete.builder().objects(keys).quiet(true).build()).build());
                deleted += keys.size();
            }
            continuationToken = page.isTruncated() ? page.nextContinuationToken() : null;
        } while (continuationToken != null);
        return deleted;
    }
}
