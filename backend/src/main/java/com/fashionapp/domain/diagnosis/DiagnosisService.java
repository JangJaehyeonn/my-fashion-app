package com.fashionapp.domain.diagnosis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiDiagnosisResponse;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final StyleDiagnosisRepository styleDiagnosisRepository;
    private final UserRepository userRepository;
    private final S3Uploader s3Uploader;
    private final AiServerClient aiServerClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public StyleDiagnosisResponse diagnose(UUID userId, MultipartFile image) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String imageUrl = s3Uploader.upload(image, "diagnosis");
        AiDiagnosisResponse aiResult = aiServerClient.diagnoseOutfit(image);

        List<SimilarStyleSuggestion> similarStyles = aiResult.getSimilarStyles().stream()
                .map(s -> new SimilarStyleSuggestion(s.getStyleTag(), s.getDescription()))
                .toList();

        StyleDiagnosis diagnosis = StyleDiagnosis.builder()
                .user(user)
                .imageUrl(imageUrl)
                .score(aiResult.getScore())
                .feedback(aiResult.getFeedback())
                .similarStyles(writeJson(similarStyles))
                .build();

        return toResponse(styleDiagnosisRepository.save(diagnosis));
    }

    @Transactional(readOnly = true)
    public List<StyleDiagnosisResponse> getMyDiagnoses(UUID userId) {
        return styleDiagnosisRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StyleDiagnosisResponse getDiagnosis(UUID userId, UUID diagnosisId) {
        StyleDiagnosis diagnosis = styleDiagnosisRepository.findByIdAndUser_Id(diagnosisId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DIAGNOSIS_NOT_FOUND));
        return toResponse(diagnosis);
    }

    private StyleDiagnosisResponse toResponse(StyleDiagnosis diagnosis) {
        return StyleDiagnosisResponse.builder()
                .id(diagnosis.getId())
                .imageUrl(s3Uploader.generatePresignedUrl(diagnosis.getImageUrl()))
                .score(diagnosis.getScore())
                .feedback(diagnosis.getFeedback())
                .similarStyles(readJson(diagnosis.getSimilarStyles()))
                .createdAt(diagnosis.getCreatedAt())
                .build();
    }

    private String writeJson(List<SimilarStyleSuggestion> similarStyles) {
        try {
            return objectMapper.writeValueAsString(similarStyles);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("similarStyles 직렬화 실패", e);
        }
    }

    private List<SimilarStyleSuggestion> readJson(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SimilarStyleSuggestion>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("similarStyles 역직렬화 실패", e);
        }
    }
}
