# 0003. 앱 ViewModel 단위 테스트 도구와 옷 상세 화면 구조

- 상태: 채택
- 날짜: 2026-11-21
- 관련: FR-02, #5

## 배경
FR-02(옷 상세·수정·재분류 화면)는 사람이 에뮬레이터로 확인하는 요구사항이라, CLAUDE.md는 ViewModel 단위 테스트로
저장·재분류의 성공·실패 상태 변화를 검증하라고 한다. 그런데 앱 모듈에는 지금까지 `android/app/src/test/`도, 테스트 의존성도 없었다.
ViewModel은 `viewModelScope`(Dispatchers.Main)를 쓰므로 JVM 테스트에서 Main 디스패처를 바꿀 수단이 필요하다.

## 결정
테스트 전용으로 `junit:junit:4.13.2`와 `kotlinx-coroutines-test`(기존 coroutines 버전과 같은 1.9.0)를 `testImplementation`으로 추가한다.
목 라이브러리는 넣지 않고, Retrofit 인터페이스(`ClothesApi`)를 직접 구현한 가짜 객체로 실제 `ClothesRepository`를 감싸 테스트한다.
`unitTests.isReturnDefaultValues = true`로 android.jar 스텁이 예외 대신 기본값을 돌려주게 한다.

옷 상세는 옷장 화면 안의 다이얼로그가 아니라 별도 내비게이션 경로(`clothes/{clothesId}`)와 별도 `ClothesDetailViewModel`로 둔다.
서버에 단건 조회 API가 없어 상세 화면은 `GET /api/clothes` 목록에서 id로 찾는다. 저장·재분류에 성공하고 돌아오면
옷장 화면이 `savedStateHandle` 결과 값을 보고 목록을 다시 불러온다.

## 검토한 대안
| 대안 | 장점 | 단점 | 버린 이유 |
| --- | --- | --- | --- |
| JUnit 5 + mockk | 표현력이 좋음 | Android Gradle 플러그인에 JUnit 5 연결 플러그인이 따로 필요하고, `useJUnitPlatform()` 누락 시 0건 통과 함정(devlog)이 있음 | 의존성이 더 늘고 함정이 있음 |
| Robolectric | 화면까지 JVM에서 실행 | 무겁고 SDK 내려받기로 CI가 느려짐 | ViewModel 상태 검증에는 과함 |
| 상세를 `ClosetViewModel` 안의 상태로 처리 | 목록 갱신이 바로 됨 | 이미 큰 ViewModel이 더 커지고, 등록 로직(Context·Uri)과 섞여 테스트가 어려움 | 테스트하기 어려움 |
| 단건 조회 API 추가 | 상세 화면 조회가 가벼움 | 요구사항에 없는 백엔드 변경 | 요구사항 범위 밖 |

## 결과
- 앱 모듈에서 `testDebugUnitTest`로 ViewModel 테스트가 실제로 돈다. 이후 앱 화면 요구사항도 같은 방식(가짜 API + 실제 Repository)을 쓴다.
- 상세 화면은 열 때마다 옷 목록 전체를 한 번 받는다. 옷장이 커져 느려지면 단건 조회 API를 별도 요구사항으로 검토한다.
