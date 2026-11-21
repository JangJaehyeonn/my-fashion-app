package com.fashionapp.infra;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 로컬 디스크 저장소 (storage.type=local, 기본값). 이미지는 {@code ImageController}가 인증을 확인하고 내려준다.
 * 저장 경로는 STORAGE_LOCAL_ROOT (기본: 사용자 홈 아래 — 프로젝트 밖).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalDiskStorage implements ImageStorage {

    private final Path root;
    private final String publicBaseUrl;
    private final long maxBytes;

    public LocalDiskStorage(@Value("${storage.local.root}") String root,
                            @Value("${app.public-base-url}") String publicBaseUrl,
                            @Value("${storage.max-bytes:10485760}") long maxBytes) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.maxBytes = maxBytes;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("이미지 저장 경로를 만들 수 없습니다: " + this.root, e);
        }
        log.info("image storage: local disk at {}", this.root);
    }

    @Override
    public String store(MultipartFile file, String category, UUID ownerId) {
        byte[] bytes = ImageKeys.validate(file, maxBytes);
        return write(bytes, file.getContentType(), category, ownerId);
    }

    @Override
    public String storeBytes(byte[] bytes, String contentType, String category, UUID ownerId) {
        ImageKeys.validateBytes(bytes, contentType, maxBytes);
        return write(bytes, contentType, category, ownerId);
    }

    private String write(byte[] bytes, String contentType, String category, UUID ownerId) {
        String key = ImageKeys.newKey(category, ownerId, contentType);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            // 쓰는 도중의 반쪽 파일이 조회되지 않도록 임시 파일에 쓴 뒤 옮긴다
            Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(tmp, bytes);
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            log.error("local image write failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
        return key;
    }

    @Override
    public String resolveUrl(String storedKey) {
        return publicBaseUrl + "/api/images/" + ImageKeys.toKey(storedKey);
    }

    /** 키 형식이 맞고 파일이 있을 때만 */
    @Override
    public Optional<Resource> load(String key) {
        if (!ImageKeys.isValidKey(key)) {
            return Optional.empty();
        }
        Path file = resolve(key);
        return Files.isRegularFile(file) ? Optional.of(new FileSystemResource(file)) : Optional.empty();
    }

    @Override
    public Optional<byte[]> readBytes(String storedKey) {
        Optional<Resource> resource = load(ImageKeys.toKey(storedKey));
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(resource.get().getContentAsByteArray());
        } catch (IOException e) {
            log.warn("local image read failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(String storedKey) {
        String key = ImageKeys.toKey(storedKey);
        if (!ImageKeys.isValidKey(key)) {
            log.warn("local image delete skipped, not a storage key: {}", key);
            return;
        }
        Path file = resolve(key);
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent()); // 사용자 폴더가 비었을 때만 지워진다
        } catch (DirectoryNotEmptyException ignored) {
            // 같은 사용자의 다른 파일이 남아 있음
        } catch (IOException e) {
            throw new IllegalStateException("local image delete failed: " + key, e);
        }
    }

    @Override
    public int deleteByPrefix(String prefix) {
        if (!ImageKeys.isValidPrefix(prefix)) {
            throw new IllegalArgumentException("invalid prefix: " + prefix);
        }
        Path dir = resolve(prefix);
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int count = 0;
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList(); // 파일 먼저, 폴더는 마지막
            for (Path p : paths) {
                if (Files.isRegularFile(p)) {
                    count++;
                }
                Files.delete(p);
            }
        } catch (IOException e) {
            throw new IllegalStateException("local image prefix delete failed: " + prefix, e);
        }
        return count;
    }

    /** 키(또는 prefix)를 저장 루트 아래의 실제 경로로. 루트를 벗어나면 거부 (키 형식 검증과 별개로 한 번 더 방어) */
    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root) || p.equals(root)) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FORMAT);
        }
        return p;
    }
}
