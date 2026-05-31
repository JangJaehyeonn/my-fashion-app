package com.fashionapp.infra;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final RestClient restClient;

    @Value("${app.ai-server-url}")
    private String aiServerUrl;

    public AiClassifyResponse classifyClothes(MultipartFile file) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            return restClient.post()
                    .uri(aiServerUrl + "/ai/clothes/classify")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(AiClassifyResponse.class);

        } catch (IOException | RestClientException e) {
            log.error("AI server classify failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.AI_SERVER_ERROR);
        }
    }
}
