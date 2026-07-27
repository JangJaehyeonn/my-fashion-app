# 트러블슈팅 기록

> 1인 개발로 진행한 AI 패션 코디 앱 프로젝트에서 겪은 주요 트러블슈팅을
> `CLAUDE.md` 개발 일지를 바탕으로 정리한 문서입니다. 각 사례는 **문제 상황 →
> 원인 분석 → 해결 방법 → 결과** 순서로 기술했습니다.

---

## 목차

1. [소셜 로그인 — Kakao 로그인 KOE205 오류](#1-소셜-로그인--kakao-로그인-koe205-오류)
2. [소셜 로그인 — OAuth2 redirect_uri_mismatch](#2-소셜-로그인--oauth2-redirect_uri_mismatch)
3. [인증 — 미인증 요청이 401 대신 302로 응답됨](#3-인증--미인증-요청이-401-대신-302로-응답됨)
4. [인증 — 앱 재시작 시 토큰 만료로 빈 화면 발생](#4-인증--앱-재시작-시-토큰-만료로-빈-화면-발생)
5. [이미지 업로드 — MIME 타입 불일치로 인한 400 오류](#5-이미지-업로드--mime-타입-불일치로-인한-400-오류)
6. [이미지 조회 — S3 Pre-signed URL 미적용으로 인한 403 오류](#6-이미지-조회--s3-pre-signed-url-미적용으로-인한-403-오류)
7. [서버 간 통신 — Spring Boot ↔ FastAPI 멀티파트 전송 422 오류](#7-서버-간-통신--spring-boot--fastapi-멀티파트-전송-422-오류)
8. [API 설계 — Pydantic null 미허용으로 인한 422 오류](#8-api-설계--pydantic-null-미허용으로-인한-422-오류)
9. [Jetpack Compose — 파생 상태 미갱신으로 필터 동작 안 함](#9-jetpack-compose--파생-상태-미갱신으로-필터-동작-안-함)
10. [배포 — Docker 이미지 재빌드 누락으로 인한 반복적 404/기능 미반영](#10-배포--docker-이미지-재빌드-누락으로-인한-반복적-404기능-미반영)
11. [빌드 설정 — 빌드 타입별 환경 분리 누락으로 인한 API 호출 실패](#11-빌드-설정--빌드-타입별-환경-분리-누락으로-인한-api-호출-실패)
12. [코드 관리 — 파일명과 실제 역할 불일치로 인한 오탐 삭제](#12-코드-관리--파일명과-실제-역할-불일치로-인한-오탐-삭제)

---

## 1. 소셜 로그인 — Kakao 로그인 KOE205 오류

**문제 상황**
카카오 소셜 로그인 시도 시 `KOE205` 오류가 발생하며 로그인이 완료되지 않았다.

**원인 분석**
두 가지 원인이 겹쳐 있었다.
1. `application.yml`의 카카오 OAuth2 scope에 `account_email`이 남아 있었는데, 이는 카카오 앱의 심사/권한 설정과 맞지 않아 거부되는 항목이었다 (grep으로 전체 설정을 훑기 전에는 눈에 잘 띄지 않는 위치에 있었음).
2. `User.email` 컬럼이 `nullable = false`로 선언되어 있어, 이메일 동의를 받지 못한 사용자의 경우 DB 저장 단계에서 실패할 소지가 있었다.

**해결 방법**
- 카카오 scope에서 `account_email` 제거
- `User.email` 컬럼을 `nullable = true`로 변경

**결과**
카카오 로그인 플로우가 정상 동작하도록 수정, 이후 에뮬레이터 E2E 테스트로 통과 확인.

---

## 2. 소셜 로그인 — OAuth2 redirect_uri_mismatch

**문제 상황**
Google/Kakao 로그인 시 `redirect_uri_mismatch` 오류가 발생했다. Spring Boot 서버 자체는 정상 기동 중이었다.

**원인 분석**
Android 에뮬레이터는 호스트 PC를 `10.0.2.2`로 접근해야 하기 때문에 `OAUTH2_BASE_URL`을 `http://10.0.2.2:8080`으로 설정해두었는데, Spring Security가 이 값으로 `redirect_uri`를 생성하다 보니 `http://10.0.2.2:8080/login/oauth2/code/{provider}` 형태가 되었다. 그런데 Google/Kakao 개발자 콘솔은 **IP 주소를 redirect URI로 등록하는 것을 허용하지 않고 `localhost`만 허용**한다.

**해결 방법**
- `OAUTH2_BASE_URL`만 `http://localhost:8080`으로 변경 (Retrofit API 호출용 `BASE_URL`은 `10.0.2.2` 그대로 유지 — 용도가 다르므로 분리)
- 에뮬레이터에서 `adb reverse tcp:8080 tcp:8080` 실행으로 localhost 포트포워딩
- Google Console / Kakao Developers 콘솔에 `http://localhost:8080/login/oauth2/code/{google|kakao}` 등록

**결과**
Google/Kakao 로그인 모두 에뮬레이터 E2E 테스트 통과. 이후 실서버 배포 시에는 EC2 도메인으로 별도 값을 사용하도록 `release`/`debug` 빌드 타입을 분리(11번 사례 참고)하는 계기가 되었다.

---

## 3. 인증 — 미인증 요청이 401 대신 302로 응답됨

**문제 상황**
로그인하지 않은 상태로 보호된 API를 호출했을 때, 클라이언트가 기대하는 `401 Unauthorized` 대신 `302` 리다이렉트(OAuth2 인증 페이지로)가 반환되었다. 그 결과 Axios/OkHttp의 토큰 자동 갱신 인터셉터가 전혀 동작하지 않았다.

**원인 분석**
Spring Security 설정(`SecurityConfig`)에 `AuthenticationEntryPoint`가 별도로 지정되어 있지 않아, 기본 동작(로그인 페이지로 리다이렉트)이 그대로 적용되고 있었다. REST API 서버에서는 이 기본 동작이 적합하지 않았다.

**해결 방법**
`SecurityConfig`에 람다 기반 `AuthenticationEntryPoint`를 추가해, 미인증 요청에 대해 항상 `HTTP 401 + JSON` 형태로 응답하도록 수정.

**결과**
클라이언트의 401 감지 → 토큰 갱신 → 재요청 흐름이 정상적으로 동작하게 됨. 이후 만든 모든 인증 관련 기능(토큰 자동 갱신 등)의 전제 조건이 되는 수정이었다.

---

## 4. 인증 — 앱 재시작 시 토큰 만료로 빈 화면 발생

**문제 상황**
앱을 완전히 종료했다가 다시 실행하면 로그인 상태임에도 불구하고 데이터 목록 화면이 빈 화면으로 나타났다.

**원인 분석**
`ApiClient.kt`에 401 응답을 받았을 때 자동으로 refresh token을 사용해 토큰을 재발급받는 로직이 없었다. Access token 만료 후 첫 요청이 그대로 실패로 끝나버리는 구조였다.

**해결 방법**
`okhttp3.Authenticator`를 구현한 `TokenAuthenticator`를 추가했다.
- 401 응답을 받으면 별도의 `OkHttpClient` 인스턴스로 `POST /api/auth/refresh`를 직접 호출 (기존 `ApiClient`를 그대로 쓰면 순환 의존이 발생하므로 분리)
- 새로 받은 토큰을 `TokenDataStore`에 저장하고 원본 요청을 새 토큰으로 재시도
- `priorResponse` 체인 카운트가 2 이상이면 무한 재시도를 막기 위해 `null` 반환
- refresh 자체가 실패하면 저장된 토큰을 모두 clear

**결과**
앱을 재시작해도 토큰이 자동으로 갱신되어 정상적으로 데이터가 로드됨을 확인.

---

## 5. 이미지 업로드 — MIME 타입 불일치로 인한 400 오류

**문제 상황**
갤러리에서 사진을 선택해 업로드하면 서버가 `INVALID_IMAGE_FORMAT (400)`을 반환했다.

**원인 분석**
Android 클라이언트가 Content-Type을 항상 `"image/*"`라는 와일드카드 문자열로 전송하고 있었는데, 서버(`S3Uploader.java`)의 허용 목록에는 `image/jpeg`, `image/png`, `image/webp`, `image/heic` 같은 구체적인 MIME 타입만 등록되어 있어 매칭에 실패했다.

**해결 방법**
`context.contentResolver.getType(uri)`로 실제 파일의 MIME 타입을 조회한 뒤, 그 값을 그대로 Content-Type으로 전송하도록 수정.

**결과**
실제 이미지 형식에 맞는 Content-Type이 전달되어 400 오류 해소. 이후 코디 진단 기능(Phase 2)에서 동일한 이미지 업로드를 구현할 때도 이 패턴을 그대로 재사용해 같은 함정을 재발하지 않도록 함.

---

## 6. 이미지 조회 — S3 Pre-signed URL 미적용으로 인한 403 오류

**문제 상황**
이미지 업로드 API는 200 OK를 반환했지만, 정작 앱 화면에는 이미지가 표시되지 않았다.

**원인 분석**
S3 버킷에 Block Public Access가 설정되어 있어, DB에 저장된 정적 S3 URL(`https://{bucket}.s3.{region}.amazonaws.com/{key}`)로 직접 접근하면 403 Forbidden이 반환되는 상태였다.

**해결 방법**
- `S3Config`에 `S3Presigner` 빈 추가
- `S3Uploader`에 `generatePresignedUrl()` 메서드를 추가해 7일 유효한 서명 URL을 생성
- 서비스 계층에 `toResponse()` 헬퍼를 두어, DB에는 정적 URL을 그대로 저장하되 **API 응답 시점에만** pre-signed URL로 변환해 내려주도록 분리

**결과**
Coil(`AsyncImage`)이 pre-signed URL을 정상적으로 로드해 이미지가 화면에 표시됨. "저장은 정적 URL, 노출은 서명 URL"로 책임을 분리한 덕분에 이후 URL 만료 정책을 변경할 때도 API 계층만 건드리면 되는 구조가 되었다.

---

## 7. 서버 간 통신 — Spring Boot ↔ FastAPI 멀티파트 전송 422 오류

**문제 상황**
Spring Boot가 이미지를 FastAPI(AI 서버)로 멀티파트 전송할 때 `422 Unprocessable Entity`(`"Field required"`)가 반환되었다.

**원인 분석**
Spring Boot의 `RestClient`가 내부적으로 JDK `HttpClient`를 사용하면서 `Expect: 100-continue` 헤더를 함께 보냈다. 그런데 uvicorn(FastAPI 서버)이 `100 Continue` 응답을 반환하지 않아, FastAPI가 body가 비어 있는 상태로 멀티파트 파싱을 시도해 필수 필드 누락 오류를 냈다. 이후 Spring Boot가 body를 뒤늦게 전송하면서 uvicorn 쪽에는 `"Invalid HTTP request received"` 경고까지 남았다.

**해결 방법**
- `RestClientConfig`에서 `SimpleClientHttpRequestFactory`를 사용하도록 변경해 `Expect: 100-continue` 헤더 전송을 비활성화
- `AiServerClient`의 파일 전송 로직을 `MultipartBodyBuilder` 방식으로 다시 작성해 Content-Type과 filename을 명시적으로 설정

**결과**
FastAPI가 멀티파트 body를 정상적으로 파싱. 이 문제는 "400(클라이언트 MIME) → 503(AI 서버 미기동) → 422(Expect 헤더) → 500(OpenAI가 부적절한 이미지 거부)" 순서로 이어진 연쇄적인 디버깅이었고, 각 단계를 표로 정리해두어 재발 시 원인을 빠르게 좁힐 수 있도록 했다.

---

## 8. API 설계 — Pydantic null 미허용으로 인한 422 오류

**문제 상황**
AI 코디 추천 API 호출 시 간헐적으로 `422` 오류가 발생했다.

**원인 분석**
FastAPI 쪽 Pydantic 스키마의 `category`, `color`, `pattern`, `season`, `style_tag` 필드가 전부 non-nullable `str`로 선언되어 있었는데, 실제 DB에는 아직 분류되지 않아 해당 필드가 `null`인 데이터가 존재했다. 클라이언트 입력값 검증이 아니라 **서버 간 내부 데이터 전달 스키마**가 실제 데이터 상태를 반영하지 못한 경우였다.

**해결 방법**
- 해당 필드를 전부 `Optional[str] = None`으로 변경
- 서비스 로직에서 `null` 값을 `"미분류"`로 치환해 프롬프트에 포함되도록 처리

**결과**
분류되지 않은 데이터가 섞여 있어도 추천 API가 예외 없이 동작. "내부 서비스 간 스키마도 실제 데이터의 null 가능성을 기준으로 설계해야 한다"는 교훈을 얻은 사례.

---

## 9. Jetpack Compose — 파생 상태 미갱신으로 필터 동작 안 함

**문제 상황**
목록 화면에서 카테고리 탭을 눌러도 필터링된 결과가 반영되지 않았다.

**원인 분석**
`filteredClothes`가 `ViewModel`의 plain getter(연산 프로퍼티)로 구현되어 있었다. Compose는 `State`/`StateFlow`의 변경을 구독해 리컴포지션을 트리거하는데, plain getter는 변경 알림을 발생시키지 않으므로 원본 데이터(`_clothes`)가 바뀌어도 화면이 이를 감지하지 못했다.

**해결 방법**
`combine(_clothes, _selectedCategory) { ... }.stateIn(...)` 형태로 파생 상태를 명시적인 `StateFlow`로 변환하고, Compose 쪽에서는 `collectAsState()`로 구독하도록 수정.

**결과**
카테고리 필터가 정상 동작. 이후 "파생 상태는 plain getter가 아니라 `combine().stateIn()`으로 `StateFlow`화해서 노출한다"는 원칙을 팀(1인 개발이지만) 컨벤션으로 세워, 유사한 실수를 예방.

---

## 10. 배포 — Docker 이미지 재빌드 누락으로 인한 반복적 404/기능 미반영

**문제 상황**
AI 서버(FastAPI) 코드를 수정했는데도 실제 호출 시 새로 추가한 라우트가 `404 Not Found`로 응답하거나, 수정한 로직이 반영되지 않는 문제가 **한 번이 아니라 프로젝트 전체에 걸쳐 여러 차례 반복**됐다.

**원인 분석**
`docker-compose.yml`에서 `ai-server`는 볼륨 마운트 없이 `build: ./ai-server`로 이미지를 굽는 방식이었다. 즉 소스 코드 파일 자체는 최신이어도, 컨테이너가 실행 중인 이미지는 몇 주 전에 빌드된 상태 그대로였다. 코드 변경과 배포 반영이 분리되어 있다는 사실을 매번 깜빡하기 쉬운 구조였다.

**해결 방법**
- 코드를 수정할 때마다 `docker compose build ai-server && docker compose up -d ai-server`로 이미지를 재빌드·재기동하는 것을 표준 절차로 고정
- 재발 시마다 CLAUDE.md 개발 일지에 "n번째 동일 유형 이슈"라고 명시적으로 기록해, 다음에도 같은 원인을 가장 먼저 의심하도록 함
- (후속 개선 아이디어로 볼륨 마운트 + `--reload` 적용을 검토했으나, 우선순위상 아직 미적용)

**결과**
동일 유형의 문제가 재발할 때마다 원인 파악에 걸리는 시간이 크게 줄었다. "인프라/배포 파이프라인의 특성(빌드 방식)을 이해하지 못하면 애플리케이션 코드는 맞아도 장애가 발생한다"는 점을 체감한 사례.

---

## 11. 빌드 설정 — 빌드 타입별 환경 분리 누락으로 인한 API 호출 실패

**문제 상황**
로컬 백엔드에 새로 추가한 "체형/취향 프로필 저장" API가 에뮬레이터에서 항상 실패했다.

**원인 분석**
`android/app/build.gradle.kts`의 `BASE_URL`/`OAUTH2_BASE_URL`이 `defaultConfig`에 EC2 운영 도메인(`fashion-app-jh.duckdns.org`)으로 고정되어 있었다. 그 결과 디버그 빌드(에뮬레이터 실행)로 테스트해도 항상 EC2 운영 서버로 요청이 나갔는데, 방금 로컬에만 추가한 신규 API는 아직 EC2에 배포되지 않은 상태였다.

**해결 방법**
`buildTypes.debug`/`buildTypes.release`에 각각 `buildConfigField`를 분리 정의.
- debug: `BASE_URL = http://10.0.2.2:8080/api/`, `OAUTH2_BASE_URL = http://localhost:8080`
- release: 기존 EC2 도메인 그대로 유지

**결과**
디버그 빌드는 로컬 서버로, 릴리즈 빌드는 운영 서버로 정확히 분기되어 이후 로컬 개발/테스트가 실제 배포 상태와 뒤섞이지 않게 됨. `BuildConfig` 값은 빌드 시점에 고정되므로, 로컬 테스트 때마다 디버그 APK를 재빌드해야 한다는 제약도 함께 문서화.

---

## 12. 코드 관리 — 파일명과 실제 역할 불일치로 인한 오탐 삭제

**문제 상황**
서비스 방향 피봇에 따라 네비게이션 코드를 정리하던 중, `AppNavigation.kt`를 수정했더니 `MainViewModel`/`NavEvent` 클래스가 통째로 사라지는 사고가 발생했다.

**원인 분석**
이 프로젝트의 `ui/navigation/` 폴더는 파일명과 실제 내용이 직관과 반대로 매칭되어 있었다. `Navigation.kt`에 `Route` object와 `AppNavigation()` 컴포저블이 들어 있고, 오히려 `AppNavigation.kt`에는 `MainViewModel`/`NavEvent`가 들어 있었다. 파일명만 보고 "AppNavigation.kt = 네비게이션 관련 파일"이라고 넘겨짚어 잘못된 파일에 편집을 가한 것이 원인이었다.

**해결 방법**
- `git status`로 의도치 않게 `AppNavigation.kt`만 modified로 표시된 것을 발견
- `git checkout -- AppNavigation.kt`로 원본 복구
- 실제 Route/NavHost 내용을 담고 있던 `Navigation.kt` 쪽에 옷장/캘린더 제거 편집을 다시 적용

**결과**
사고 없이 정리 완료. 이후 "이 폴더는 파일명만 보고 넘겨짚지 말고 반드시 내용을 먼저 확인할 것"이라는 재발 방지 메모를 CLAUDE.md에 남겨, 팀(1인 개발이지만 미래의 자신 포함)이 같은 실수를 반복하지 않도록 함. **git status를 습관적으로 확인하는 것만으로 실수를 조기에 발견할 수 있다**는 점을 보여주는 사례이기도 하다.

---

## 돌아보며

프로젝트 전반에서 반복적으로 얻은 교훈은 다음과 같다.

- **에러 메시지의 표면(400/422/403/404)만 보지 않고, 요청이 지나가는 각 계층(클라이언트 → 백엔드 → AI 서버 → 외부 API)을 하나씩 짚어가며 원인을 좁히는 습관**이 디버깅 시간을 크게 줄여줬다.
- 같은 유형의 문제(Docker 이미지 미반영, MIME 타입 불일치)가 여러 번 반복되었는데, 이를 **그때그때 고치고 끝내지 않고 문서(CLAUDE.md)에 남겨 재발 방지 절차로 굳힌 것**이 이후 동일 문제의 해결 속도를 크게 단축시켰다.
- 인증/배포처럼 겉보기엔 "설정값 하나"의 문제가 실제로는 **플랫폼 제약(Google/Kakao가 IP를 허용하지 않는 정책, 에뮬레이터의 네트워크 격리, Docker 빌드 방식)**을 이해해야 풀리는 경우가 많았다.
