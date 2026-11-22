# 0006. 옷 임베딩 저장 방식 (pgvector, 네이티브 SQL 매핑)

- 상태: 채택
- 날짜: 2026-11-22
- 관련: FR-07, #23

## 배경
FR-07은 PostgreSQL에 pgvector를 켜고 `clothes.embedding vector(1536)`에 옷 임베딩을 저장하라고 한다. 이후 FR-08(유사한 옷 찾기)이
이 벡터로 유사도를 계산한다. 제약은 다음과 같다.
- 인수 테스트 하네스와 로컬 compose는 pgvector 이미지를 쓰지만, 백엔드 통합 테스트(CI의 `setup-wearon`)와 현재 운영 compose는
  확장이 없는 `postgres:16-alpine`을 쓴다. 이 둘은 이 PR에서 바꿀 수 없거나(`.github/`) 운영 DB 이미지 교체가 따로 필요하다.
- 임베딩 실패는 옷 등록 실패로 이어지면 안 된다. 새 컬럼은 nullable 추가만 허용된다.
- 새 의존성은 가능하면 늘리지 않는다.

## 결정
- `embedding` 컬럼은 JPA 엔티티(`Clothes`)에 매핑하지 않는다. `ClothesEmbeddingStore`가 `JdbcTemplate`로
  `UPDATE clothes SET embedding = CAST(? AS vector)`처럼 pgvector 텍스트 형식(`[v1,v2,...]`)으로 읽고 쓴다.
- 기동 시 `ClothesEmbeddingStore`(ApplicationRunner)가 `CREATE EXTENSION IF NOT EXISTS vector`와
  `ALTER TABLE clothes ADD COLUMN IF NOT EXISTS embedding vector(1536)`를 실행한다. 같은 내용의 멱등 스크립트는
  `db/phase10_clothes_embedding.sql`. 확장을 켤 수 없으면 경고만 남기고 임베딩 기능을 끈다(AI 호출도 하지 않는다).
- fit·material·pattern·season은 일반 nullable 문자열 컬럼으로 엔티티에 매핑한다(ddl-auto가 추가).
- 임베딩 텍스트는 `카테고리(한국어) 색상 핏 소재 패턴 이름`을 공백으로 이어 붙이고 null·빈 값은 뺀다. 미분류 옷은 임베딩하지 않는다.
  등록·PATCH·재분류 뒤에 다시 계산하고, 실패하거나 응답 형식(1536개 유한 실수)이 틀리면 옛 값을 지워 비워 둔다(낡은 벡터가 남지 않게).
- 로컬 compose의 postgres 이미지를 `pgvector/pgvector:pg16`으로 바꾼다.

## 검토한 대안
| 대안 | 장점 | 단점 | 버린 이유 |
| --- | --- | --- | --- |
| `hibernate-vector`(`@JdbcTypeCode(SqlTypes.VECTOR)`)로 엔티티에 매핑 | 엔티티에서 바로 읽고 씀 | Spring Boot 3.2의 Hibernate 6.4와 맞는 버전 의존성 추가, ddl-auto가 확장 없는 DB에서 컬럼 생성에 실패하고 모든 옷 INSERT가 깨짐 | 확장 없는 DB(통합 테스트·현재 운영)에서 옷 기능 전체가 멈춤 |
| `pgvector-java`의 `PGvector` 타입 + 커스텀 UserType | 타입 안전 | 새 의존성, 엔티티 매핑이라 위와 같은 INSERT 문제 | 위와 같음 |
| 벡터를 `float8[]`나 JSON으로 저장 | 확장 불필요 | 요구사항(vector(1536))과 다름, FR-08 유사도 계산을 DB에서 못 함 | 요구사항 |
| 임베딩을 별도 테이블(`clothes_embeddings`)에 저장 | clothes 테이블 무변경 | 요구사항은 clothes.embedding, 조인 필요 | 요구사항 |

## 결과
- 확장이 있든 없든 옷 등록·조회·수정은 그대로 동작한다. 확장이 없으면 임베딩만 비어 있다.
- 벡터를 읽는 쪽(FR-08 유사도 검색)도 엔티티가 아니라 `ClothesEmbeddingStore`의 네이티브 SQL로 구현해야 한다.
- 운영 DB에서 임베딩을 쓰려면 운영 compose의 postgres 이미지를 `pgvector/pgvector:pg16`으로 바꾸는 배포가 따로 필요하다
  (같은 PostgreSQL 16이라 데이터 디렉터리는 그대로 쓰지만, alpine(musl)과 Debian(glibc)의 정렬 규칙 차이가 있어 바꾼 뒤 인덱스 재색인을 확인한다).
- 기존 옷은 임베딩이 비어 있다. 사용자가 속성을 고치거나 재분류할 때 채워진다. 일괄 백필이 필요해지면 별도 작업으로 다룬다.
- 차원(1536)은 `text-embedding-3-small` 기준이다. 모델을 바꾸면 컬럼 차원과 기존 벡터를 함께 다시 정한다.
