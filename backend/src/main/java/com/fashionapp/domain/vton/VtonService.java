package com.fashionapp.domain.vton;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.AiVtonResponse;
import com.fashionapp.infra.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class VtonService {

    private final UserRepository userRepository;
    private final AiServerClient aiServerClient;
    private final ImageStorage imageStorage;

    // 결과 이미지는 DB에 기록이 없어 vton/{userId}/ 아래에 저장 → 회원 탈퇴 시 prefix로 한꺼번에 삭제
    public static String storageDir(UUID userId) {
        return "vton/" + userId;
    }

    // AI 서버 호출(수 초~수 분)을 기다리는 동안 DB 커넥션을 붙잡지 않도록 트랜잭션을 걸지 않음 —
    // 조회·저장은 각 리포지토리 호출이 짧은 트랜잭션으로 처리 (open-in-view도 꺼져 있어야 함, application.yml)
    public VtonResponse tryOn(UUID userId, MultipartFile personImage, MultipartFile garmentImage, String garmentDesc) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        // 전신 사진을 외부(Hugging Face 공개 Space)로 보내기 전에 동의를 서버에서 확인 — 앱 화면만 믿지 않음
        if (!user.hasVtonConsent()) {
            throw new CustomException(ErrorCode.VTON_CONSENT_REQUIRED);
        }

        AiVtonResponse aiResult;
        try {
            aiResult = aiServerClient.virtualTryOn(personImage, garmentImage, garmentDesc).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof CustomException customException) {
                throw customException;
            }
            throw e;
        }

        byte[] resultBytes = Base64.getDecoder().decode(aiResult.getResultImageBase64());
        String imageUrl = imageStorage.storeBytes(resultBytes, "image/png", "vton", userId);

        return VtonResponse.builder()
                .resultImageUrl(imageStorage.resolveUrl(imageUrl))
                .build();
    }
}
