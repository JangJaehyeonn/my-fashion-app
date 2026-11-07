package com.fashionapp.global.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** ProdConfigValidator 실패를 스택 트레이스 대신 읽기 쉬운 안내로 출력 (META-INF/spring.factories로 등록) */
public class ProdConfigFailureAnalyzer extends AbstractFailureAnalyzer<ProdConfigValidator.ProdConfigException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, ProdConfigValidator.ProdConfigException cause) {
        return new FailureAnalysis(
                "운영 프로필(prod)의 필수 설정이 비어 있거나 로컬 값입니다:\n - " + String.join("\n - ", cause.getProblems()),
                "docs/DEPLOY_TODO.md와 backend/.env.example을 보고 위 환경변수를 설정한 뒤 다시 실행하세요.",
                cause);
    }
}
