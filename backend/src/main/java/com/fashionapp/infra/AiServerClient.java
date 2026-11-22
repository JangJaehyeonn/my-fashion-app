package com.fashionapp.infra;

import com.fashionapp.global.config.RestClientConfig;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class AiServerClient {

    private final RestClient restClient;
    // 가상 피팅 전용 — read 타임아웃이 일반 호출보다 김 (RestClientConfig 참고)
    private final RestClient vtonRestClient;
    private final Executor aiServerExecutor;
    private final String aiServerUrl;

    public AiServerClient(@Qualifier(RestClientConfig.AI_REST_CLIENT) RestClient restClient,
                          @Qualifier(RestClientConfig.AI_VTON_REST_CLIENT) RestClient vtonRestClient,
                          Executor aiServerExecutor,
                          @Value("${app.ai-server-url}") String aiServerUrl) {
        this.restClient = restClient;
        this.vtonRestClient = vtonRestClient;
        this.aiServerExecutor = aiServerExecutor;
        this.aiServerUrl = aiServerUrl;
    }

    /**
     * 연결·응답 타임아웃이면 AI_SERVER_TIMEOUT(504), 그 밖의 통신 실패·AI 서버 오류 응답은 AI_SERVER_ERROR(503).
     * 타임아웃은 SocketTimeoutException이 감싸져서 온다 — 연결 단계면 ResourceAccessException,
     * 응답(헤더·본문)을 읽는 도중이면 "Error while extracting response" RestClientException 안에 들어 있어 원인 체인 전체를 본다.
     */
    static CustomException toAiException(String operation, Exception e) {
        if (isTimeout(e)) {
            log.warn("AI server {} timed out: {}", operation, e.getMessage());
            return new CustomException(ErrorCode.AI_SERVER_TIMEOUT);
        }
        log.error("AI server {} failed: {}", operation, e.getMessage());
        return new CustomException(ErrorCode.AI_SERVER_ERROR);
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException) return true;
        }
        return false;
    }

    public CompletableFuture<AiSituationRecommendResponse> recommendOutfitsBySituation(AiSituationRecommendRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return restClient.post()
                        .uri(aiServerUrl + "/ai/outfits/recommend/situation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(AiSituationRecommendResponse.class);
            } catch (RestClientException e) {
                throw toAiException("situation recommend", e);
            }
        }, aiServerExecutor);
    }

    public CompletableFuture<AiClosetRecommendResponse> recommendOutfitsByCloset(AiClosetRecommendRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return restClient.post()
                        .uri(aiServerUrl + "/ai/outfits/recommend/closet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(AiClosetRecommendResponse.class);
            } catch (RestClientException e) {
                throw toAiException("closet recommend", e);
            }
        }, aiServerExecutor);
    }

    public CompletableFuture<AiShoppingRecommendResponse> recommendShopping(AiShoppingRecommendRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return restClient.post()
                        .uri(aiServerUrl + "/ai/shopping/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(AiShoppingRecommendResponse.class);
            } catch (RestClientException e) {
                throw toAiException("shopping recommend", e);
            }
        }, aiServerExecutor);
    }

    public CompletableFuture<AiVtonResponse> virtualTryOn(MultipartFile personImage, MultipartFile garmentImage, String garmentDesc) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MultipartBodyBuilder builder = new MultipartBodyBuilder();
                addImagePart(builder, "person_image", personImage);
                addImagePart(builder, "garment_image", garmentImage);
                builder.part("garment_desc", garmentDesc != null ? garmentDesc : "");

                return vtonRestClient.post()
                        .uri(aiServerUrl + "/ai/vton")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(builder.build())
                        .retrieve()
                        .body(AiVtonResponse.class);

            } catch (IOException | RestClientException e) {
                throw toAiException("VTON", e);
            }
        }, aiServerExecutor);
    }

    private void addImagePart(MultipartBodyBuilder builder, String name, MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg";
        String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() { return filename; }
        };

        builder.part(name, resource)
                .filename(filename)
                .contentType(MediaType.parseMediaType(contentType));
    }

    public AiClothesClassifyResponse classifyClothes(MultipartFile file) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            addImagePart(builder, "image", file);
            return postClassify(builder);
        } catch (IOException | RestClientException e) {
            throw toAiException("clothes classify", e);
        }
    }

    /** Classify an image the server already stores (reclassification). */
    public AiClothesClassifyResponse classifyClothes(byte[] bytes, String filename, String contentType) {
        try {
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() { return filename; }
            };
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("image", resource)
                    .filename(filename)
                    .contentType(MediaType.parseMediaType(contentType));
            return postClassify(builder);
        } catch (RestClientException e) {
            throw toAiException("clothes classify", e);
        }
    }

    private AiClothesClassifyResponse postClassify(MultipartBodyBuilder builder) {
        return restClient.post()
                .uri(aiServerUrl + "/ai/clothes/classify")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(AiClothesClassifyResponse.class);
    }

    /** FR-07: text embedding (text-embedding-3-small on the AI server). */
    public AiEmbedResponse embed(String text) {
        try {
            return restClient.post()
                    .uri(aiServerUrl + "/ai/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .body(AiEmbedResponse.class);
        } catch (RestClientException e) {
            throw toAiException("embed", e);
        }
    }

    /** FR-08: batch text embedding (at most 100 texts, AI server limit). */
    public AiEmbedBatchResponse embedBatch(List<String> texts) {
        try {
            return restClient.post()
                    .uri(aiServerUrl + "/ai/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("texts", texts))
                    .retrieve()
                    .body(AiEmbedBatchResponse.class);
        } catch (RestClientException e) {
            throw toAiException("embed batch", e);
        }
    }

    /** FR-11: representative combos and reason sentences for a rule-decided purchase check. */
    public AiPurchaseExplainResponse explainPurchase(AiPurchaseExplainRequest request) {
        try {
            return restClient.post()
                    .uri(aiServerUrl + "/ai/purchase-check/explain")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiPurchaseExplainResponse.class);
        } catch (RestClientException e) {
            throw toAiException("purchase explain", e);
        }
    }

    public AiDiagnosisResponse diagnoseOutfit(MultipartFile file) {
        try {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg";
            String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() { return filename; }
            };

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("image", resource)
                    .filename(filename)
                    .contentType(MediaType.parseMediaType(contentType));

            return restClient.post()
                    .uri(aiServerUrl + "/ai/diagnosis")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(AiDiagnosisResponse.class);

        } catch (IOException | RestClientException e) {
            throw toAiException("diagnosis", e);
        }
    }
}
