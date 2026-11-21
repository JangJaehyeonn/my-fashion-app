# 0004. 비밀값은 기본값 없이 받고 기동 시 모든 프로필에서 검사한다

- 상태: 채택
- 날짜: 2026-11-21
- 관련: FR-03, #6

## 배경
`application.yml`에 `${DB_PASSWORD:fashionapp}`처럼 비밀값 기본값이 남아 있어, 변수를 빠뜨려도 조용히 기본값으로 기동됐다.
`${JWT_SECRET}`처럼 기본값이 없는 자리는 기동이 멈추긴 하지만, 오류가 빈 생성 실패 스택 트레이스 깊숙이 묻혀 어떤 변수가 빠졌는지 찾기 어렵다.
운영 전용 값은 이미 `ProdConfigValidator`(prod 프로필 한정)가 검사한다.

## 결정
비밀값(`JWT_SECRET`, `DB_PASSWORD`, `GOOGLE_CLIENT_SECRET`, `KAKAO_CLIENT_SECRET`)은 `application*.yml`에서 기본값 없는 `${X}`로만 받는다.
프로필과 상관없이 도는 `RequiredSecretsValidator`(EnvironmentPostProcessor)가 컨텍스트 생성 전에 값이 비었거나 풀리지 않은 것을 모두 모아
`RequiredSecretsFailureAnalyzer`로 빠진 변수 이름을 안내하며 기동을 중단한다.
선택 기능의 키(`REDIS_PASSWORD`, `S3_*`, `WEATHER_API_KEY`)는 비어 있어도 동작해야 하므로 빈 기본값(`${X:}`)을 유지하고 검사하지 않는다.

## 검토한 대안
| 대안 | 장점 | 단점 | 버린 이유 |
| --- | --- | --- | --- |
| 기본값만 지우고 Spring 기본 오류에 맡김 | 코드 추가 없음 | 첫 번째 빠진 값만, 스택 트레이스 속에서 보인다 | "어떤 변수가 빠졌는지 알려 준다"를 읽기 쉽게 만족하지 못함 |
| `ProdConfigValidator`에 합치기 | 클래스 하나 | prod 전용 검사라 로컬에서는 돌지 않는다 | 로컬·운영 모두 검사해야 함 |
| `@ConfigurationProperties` + `@Validated` | 표준 방식 | 빈 생성 단계라 다른 빈 오류에 가려질 수 있다 | `ProdConfigValidator`와 같은 이유로 컨텍스트 전 검사를 택함 |

## 결과
비밀값을 빠뜨리면 로컬에서도 바로 기동이 멈추고 빠진 변수 목록이 한 번에 보인다.
로컬 개발자는 `DB_PASSWORD`를 `backend/.env`에 넣어야 한다. `@ActiveProfiles("test")`가 없는 스프링 테스트는 테스트 전용 값을 받지 못해 기동하지 않으므로
테스트는 test 프로필을 쓴다. 필수 비밀값이 늘면 `RequiredSecretsValidator.REQUIRED_SECRETS`에 추가한다.
