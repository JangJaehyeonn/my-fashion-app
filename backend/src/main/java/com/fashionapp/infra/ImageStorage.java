package com.fashionapp.infra;

/**
 * 저장된 이미지 삭제. 업로드·presigned URL 발급은 {@link S3Uploader}가 맡고, 삭제만 인터페이스로 분리해
 * 회원 탈퇴처럼 여러 장을 지우는 흐름을 실제 S3 없이 테스트할 수 있게 한다.
 */
public interface ImageStorage {

    /** DB에 저장된 이미지 URL(정적 S3 URL) 하나를 삭제 */
    void delete(String imageUrl);

    /** 키가 prefix로 시작하는 객체를 모두 삭제하고 삭제한 개수를 반환 (예: "vton/{userId}/") */
    int deleteByPrefix(String prefix);
}
