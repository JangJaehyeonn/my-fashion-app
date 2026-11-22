package com.fashionapp.infra;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 저장 키 규칙과 업로드 이미지 검증 (저장소 구현체와 무관한 공통 로직) */
public final class ImageKeys {

    /** 허용 MIME → 확장자. 확장자는 클라이언트 파일명이 아니라 여기서 정한다 */
    private static final Map<String, String> EXT_BY_MIME = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/heic", "heic"
    );

    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1");

    private static final String UUID_RE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern KEY = Pattern.compile(
            "^(clothes|diagnosis|vton|tmp)/(" + UUID_RE + ")/(" + UUID_RE + ")\\.(jpg|png|webp|heic)$");
    private static final Pattern PREFIX = Pattern.compile("^(clothes|diagnosis|vton|tmp)/(" + UUID_RE + ")/$");

    public static final String TEMP_CATEGORY = "tmp";

    private ImageKeys() {}

    public static String newKey(String category, UUID ownerId, String contentType) {
        String key = category + "/" + ownerId + "/" + UUID.randomUUID() + "." + EXT_BY_MIME.get(contentType);
        if (!isValidKey(key)) {
            throw new IllegalArgumentException("invalid image category: " + category);
        }
        return key;
    }

    /**
     * FR-12: key of a purchase-check photo kept for 30 minutes, {@code tmp/{ownerId}/{checkId}.ext}. The check id is
     * the file name so the photo can be found (and deleted) from the check record alone.
     */
    public static String tempKey(UUID ownerId, UUID checkId, String contentType) {
        String key = TEMP_CATEGORY + "/" + ownerId + "/" + checkId + "." + EXT_BY_MIME.get(contentType);
        if (!isValidKey(key)) {
            throw new IllegalArgumentException("unsupported content type: " + contentType);
        }
        return key;
    }

    /** FR-12: prefix holding every temporary purchase-check photo of one user. */
    public static String tempPrefix(UUID ownerId) {
        return TEMP_CATEGORY + "/" + ownerId + "/";
    }

    public static boolean isValidKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    public static boolean isValidPrefix(String prefix) {
        return prefix != null && PREFIX.matcher(prefix).matches();
    }

    /** 키의 소유자 id. 형식이 맞지 않으면 null */
    public static UUID ownerOf(String key) {
        if (key == null) {
            return null;
        }
        Matcher m = KEY.matcher(key);
        return m.matches() ? UUID.fromString(m.group(2)) : null;
    }

    public static String contentTypeOf(String key) {
        String ext = key.substring(key.lastIndexOf('.') + 1);
        return EXT_BY_MIME.entrySet().stream()
                .filter(e -> e.getValue().equals(ext)).map(Map.Entry::getKey).findFirst()
                .orElse("application/octet-stream");
    }

    /** DB 값 → 키. 키가 아니라 옛 정적 URL(https://bucket.s3.region.amazonaws.com/key)이 들어 있으면 경로만 취한다 */
    public static String toKey(String stored) {
        if (stored != null && stored.startsWith("http")) {
            String path = URI.create(stored).getPath();
            return path.startsWith("/") ? path.substring(1) : path;
        }
        return stored;
    }

    /** 업로드 검증을 통과하면 바이트를 반환 */
    public static byte[] validate(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty() || file.getSize() > maxBytes) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FORMAT);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new CustomException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
        validateBytes(bytes, file.getContentType(), maxBytes);
        return bytes;
    }

    /** 선언된 MIME이 허용 목록에 있고, 파일 앞부분(매직 바이트)이 그 MIME과 일치하며, 용량이 한도 이하여야 한다 */
    public static void validateBytes(byte[] bytes, String contentType, long maxBytes) {
        if (contentType == null || !EXT_BY_MIME.containsKey(contentType)
                || bytes.length == 0 || bytes.length > maxBytes
                || !contentType.equals(sniff(bytes))) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FORMAT);
        }
    }

    private static String sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p'
                && HEIC_BRANDS.contains(new String(b, 8, 4, StandardCharsets.US_ASCII))) {
            return "image/heic";
        }
        return null;
    }
}
