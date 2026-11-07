package com.fashionapp.infra;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDiskStorageTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @TempDir Path root;
    LocalDiskStorage storage;
    UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        storage = new LocalDiskStorage(root.toString(), "http://localhost:8080/", 1024);
    }

    @Test
    @DisplayName("저장: 파일명은 서버가 만든 UUID, 확장자는 MIME에서 정하고 클라이언트 파일명은 쓰지 않는다")
    void store_usesServerGeneratedName() throws Exception {
        var file = new MockMultipartFile("image", "../../evil.php", "image/jpeg", JPEG);

        String key = storage.store(file, "clothes", owner);

        assertThat(ImageKeys.isValidKey(key)).isTrue();
        assertThat(key).startsWith("clothes/" + owner + "/").endsWith(".jpg").doesNotContain("evil");
        assertThat(Files.readAllBytes(root.resolve(key))).isEqualTo(JPEG);
        assertThat(storage.resolveUrl(key)).isEqualTo("http://localhost:8080/api/images/" + key);
    }

    @Test
    @DisplayName("검증: 허용 안 된 MIME / 내용과 MIME 불일치 / 빈 파일 / 용량 초과는 INVALID_IMAGE_FORMAT")
    void store_rejectsInvalidUploads() {
        assertInvalid(new MockMultipartFile("image", "a.gif", "image/gif", JPEG));
        assertInvalid(new MockMultipartFile("image", "a.jpg", "image/jpeg", "<?php echo 1;".getBytes()));
        assertInvalid(new MockMultipartFile("image", "a.jpg", "image/jpeg", PNG)); // PNG 내용을 JPEG로 위장
        assertInvalid(new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[0]));
        byte[] big = new byte[2000];
        System.arraycopy(JPEG, 0, big, 0, JPEG.length);
        assertInvalid(new MockMultipartFile("image", "a.jpg", "image/jpeg", big));
        assertThat(root).isEmptyDirectory();
    }

    private void assertInvalid(MockMultipartFile file) {
        assertThatThrownBy(() -> storage.store(file, "clothes", owner))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE_FORMAT);
    }

    @Test
    @DisplayName("경로 조작: 키 형식이 아니면 읽기·삭제 모두 저장 루트 밖에 닿지 않는다")
    void pathTraversal_isBlocked() throws Exception {
        Path outside = Files.createFile(root.getParent().resolve("outside-" + UUID.randomUUID() + ".jpg"));
        try {
            String evil = "../" + outside.getFileName();
            assertThat(storage.load(evil)).isEmpty();
            assertThat(storage.load("clothes/" + owner + "/../../" + outside.getFileName())).isEmpty();
            storage.delete(evil);
            assertThat(outside).exists();
            assertThatThrownBy(() -> storage.deleteByPrefix("../")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.deleteByPrefix("clothes/")).isInstanceOf(IllegalArgumentException.class);
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    @DisplayName("삭제: 개별 삭제는 해당 파일만, prefix 삭제는 그 사용자의 해당 카테고리 전부(폴더 포함)")
    void delete_andDeleteByPrefix() {
        UUID other = UUID.randomUUID();
        String a = storage.storeBytes(PNG, "image/png", "vton", owner);
        String b = storage.storeBytes(PNG, "image/png", "vton", owner);
        String c = storage.storeBytes(PNG, "image/png", "vton", other);
        String d = storage.storeBytes(JPEG, "image/jpeg", "clothes", owner);

        storage.delete(a);
        assertThat(root.resolve(a)).doesNotExist();
        assertThat(root.resolve(b)).exists();

        assertThat(storage.deleteByPrefix("vton/" + owner + "/")).isEqualTo(1);
        assertThat(root.resolve("vton/" + owner)).doesNotExist();
        assertThat(root.resolve(c)).exists();   // 다른 사용자
        assertThat(root.resolve(d)).exists();   // 다른 카테고리
        assertThat(storage.deleteByPrefix("vton/" + owner + "/")).isZero(); // 이미 없음
    }

    @Test
    @DisplayName("옛 DB 값(정적 S3 URL)도 키로 바꿔 삭제·URL 변환에 쓸 수 있다")
    void legacyUrlIsMappedToKey() {
        String key = "clothes/" + owner + "/" + UUID.randomUUID() + ".jpg";
        String legacy = "https://bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        assertThat(ImageKeys.toKey(legacy)).isEqualTo(key);
        assertThat(storage.resolveUrl(legacy)).isEqualTo("http://localhost:8080/api/images/" + key);
    }
}
