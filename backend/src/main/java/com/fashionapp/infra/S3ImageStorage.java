package com.fashionapp.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.util.List;

@Component
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Override
    public void delete(String imageUrl) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(S3Uploader.extractKey(imageUrl))
                .build());
    }

    @Override
    public int deleteByPrefix(String prefix) {
        int deleted = 0;
        String continuationToken = null;
        do {
            // 한 번에 최대 1000개씩 나열 → 같은 크기로 일괄 삭제 (DeleteObjects 한도도 1000개)
            ListObjectsV2Response page = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build());
            List<ObjectIdentifier> keys = page.contents().stream()
                    .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                    .toList();
            if (!keys.isEmpty()) {
                s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(keys).quiet(true).build())
                        .build());
                deleted += keys.size();
            }
            continuationToken = page.isTruncated() ? page.nextContinuationToken() : null;
        } while (continuationToken != null);
        return deleted;
    }
}
