package com.fashionapp.domain.clothes;

import com.fashionapp.infra.AiClothesClassifyResponse;
import com.fashionapp.infra.AiServerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Wraps AI clothes classification. Connection failure, timeout, 5xx and malformed responses all become an empty result
 * so callers can decide the fallback (register: save as unclassified, reclassify: 503).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClothesClassifier {

    private final AiServerClient aiServerClient;

    public Optional<ClothesClassification> classify(MultipartFile image) {
        return safely(() -> aiServerClient.classifyClothes(image));
    }

    public Optional<ClothesClassification> classify(byte[] bytes, String filename, String contentType) {
        return safely(() -> aiServerClient.classifyClothes(bytes, filename, contentType));
    }

    private Optional<ClothesClassification> safely(Supplier<AiClothesClassifyResponse> call) {
        try {
            Optional<ClothesClassification> result = ClothesClassification.from(call.get());
            if (result.isEmpty()) {
                log.warn("AI clothes classify returned a malformed response");
            }
            return result;
        } catch (RuntimeException e) {
            // AiServerClient already logs the cause (timeout / server error)
            log.warn("AI clothes classify unavailable: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
