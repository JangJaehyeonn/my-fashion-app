package com.fashionapp.infra;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final RestClient restClient;
    private final Executor aiServerExecutor;

    @Value("${app.ai-server-url}")
    private String aiServerUrl;

    public AiWeatherResponse getWeather() {
        try {
            return restClient.get()
                    .uri(aiServerUrl + "/ai/weather")
                    .retrieve()
                    .body(AiWeatherResponse.class);
        } catch (RestClientException e) {
            log.error("AI server weather failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.AI_SERVER_ERROR);
        }
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
                log.error("AI server situation recommend failed: {}", e.getMessage());
                throw new CustomException(ErrorCode.AI_SERVER_ERROR);
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
                log.error("AI server closet recommend failed: {}", e.getMessage());
                throw new CustomException(ErrorCode.AI_SERVER_ERROR);
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
                log.error("AI server shopping recommend failed: {}", e.getMessage());
                throw new CustomException(ErrorCode.AI_SERVER_ERROR);
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

                return restClient.post()
                        .uri(aiServerUrl + "/ai/vton")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(builder.build())
                        .retrieve()
                        .body(AiVtonResponse.class);

            } catch (IOException | RestClientException e) {
                log.error("AI server VTON failed: {}", e.getMessage());
                throw new CustomException(ErrorCode.AI_SERVER_ERROR);
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

            return restClient.post()
                    .uri(aiServerUrl + "/ai/clothes/classify")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(AiClothesClassifyResponse.class);

        } catch (IOException | RestClientException e) {
            log.error("AI server clothes classify failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.AI_SERVER_ERROR);
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
            log.error("AI server diagnosis failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.AI_SERVER_ERROR);
        }
    }
}
