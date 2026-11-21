package com.fashionapp.global.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Prints RequiredSecretsValidator failures as a readable guide instead of a stack trace (META-INF/spring.factories). */
public class RequiredSecretsFailureAnalyzer extends AbstractFailureAnalyzer<RequiredSecretsValidator.MissingSecretsException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, RequiredSecretsValidator.MissingSecretsException cause) {
        return new FailureAnalysis(
                "필수 비밀값 환경 변수가 비어 있어 기동을 중단합니다:\n - " + String.join("\n - ", cause.getVariables()),
                "backend/.env.example과 docs/LOCAL_DEV.md를 보고 위 환경 변수를 설정한 뒤 다시 실행하세요. "
                        + "비밀값에는 설정 파일 기본값이 없습니다.",
                cause);
    }
}
