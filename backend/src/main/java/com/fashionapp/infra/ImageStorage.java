package com.fashionapp.infra;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

/**
 * 이미지 저장소. 구현체는 {@code storage.type}으로 고른다: {@link LocalDiskStorage}(local, 기본) /
 * {@link S3CompatibleStorage}(s3 — AWS S3, Tencent COS 등 S3 호환 서비스).
 *
 * <p>DB에는 URL이 아니라 <b>저장 키</b>({@code category/ownerId/uuid.ext})만 저장한다. 클라이언트에 내려줄 URL은
 * {@link #resolveUrl}이 그때그때 만든다 (local: 인증이 필요한 백엔드 API URL, s3: presigned URL).
 * 키에 소유자 id가 들어 있어 본인 이미지인지 DB 조회 없이 확인할 수 있고, 탈퇴 시 prefix 삭제도 가능하다.
 */
public interface ImageStorage {

    /** 검증(MIME·매직 바이트·용량) 후 저장하고 저장 키를 반환. 파일명은 서버가 UUID로 만든다 */
    String store(MultipartFile file, String category, UUID ownerId);

    /** AI가 만든 결과 이미지처럼 서버가 가진 바이트를 저장 */
    String storeBytes(byte[] bytes, String contentType, String category, UUID ownerId);

    /** DB에 저장된 키(옛 데이터는 S3 URL)를 클라이언트가 이미지를 가져올 수 있는 URL로 변환 */
    String resolveUrl(String storedKey);

    /** 서버가 직접 이미지를 내려주는 저장소(local)만 구현한다. 없거나 키 형식이 틀리면 empty. s3는 presigned URL이라 항상 empty */
    default Optional<Resource> load(String key) {
        return Optional.empty();
    }

    /** 저장된 이미지의 바이트 (재분류처럼 서버가 이미지를 다시 보내야 할 때). 없거나 읽지 못하면 empty */
    Optional<byte[]> readBytes(String storedKey);

    /** 저장된 이미지 하나 삭제. 없으면 무시 */
    void delete(String storedKey);

    /** 키가 prefix로 시작하는 객체를 모두 삭제하고 개수를 반환 (예: "vton/{userId}/") */
    int deleteByPrefix(String prefix);
}
