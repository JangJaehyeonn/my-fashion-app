package com.fashionapp.domain.vton;

import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.AiVtonResponse;
import com.fashionapp.infra.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class VtonService {

    private final UserRepository userRepository;
    private final AiServerClient aiServerClient;
    private final S3Uploader s3Uploader;

    @Transactional(readOnly = true)
    public VtonResponse tryOn(UUID userId, MultipartFile personImage, MultipartFile garmentImage, String garmentDesc) {
        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

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
        String imageUrl = s3Uploader.uploadBytes(resultBytes, "image/png", "vton");

        return VtonResponse.builder()
                .resultImageUrl(s3Uploader.generatePresignedUrl(imageUrl))
                .build();
    }
}
