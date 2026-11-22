-- 옷 속성과 임베딩 (FR-07, 2026-11-22)
--
-- 변경 요약: pgvector 확장을 켜고, clothes에 fit·material·pattern·season(모두 nullable)과 embedding vector(1536)를 "추가"한다.
--           기존 컬럼·값은 바꾸지 않는다. 새 컬럼은 NULL(모름)로 시작한다.
--
-- 전제: DB 이미지에 pgvector가 있어야 한다 (로컬·인수 테스트는 pgvector/pgvector:pg16).
--       확장이 없는 DB에서는 CREATE EXTENSION이 실패한다. 그때 서버는 임베딩만 끄고 나머지는 정상 동작한다.
--
-- 적용: 서버가 기동할 때 ddl-auto: update가 속성 컬럼을, ClothesEmbeddingStore가 확장과 embedding 컬럼을 만든다.
--       그래서 이 스크립트가 필수는 아니다. 배포 전에 미리 적용하거나 확인하고 싶을 때 수동 실행한다. 모두 멱등.

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE clothes ADD COLUMN IF NOT EXISTS fit VARCHAR(50);
ALTER TABLE clothes ADD COLUMN IF NOT EXISTS material VARCHAR(50);
ALTER TABLE clothes ADD COLUMN IF NOT EXISTS pattern VARCHAR(50);
ALTER TABLE clothes ADD COLUMN IF NOT EXISTS season VARCHAR(10);
ALTER TABLE clothes ADD COLUMN IF NOT EXISTS embedding vector(1536);

-- 백필: 기존 옷은 속성을 모르므로 NULL 그대로 둔다 (값을 지어내지 않는다).
--       임베딩은 사용자가 속성을 고치거나 재분류할 때 서버가 계산해 채운다. 이 스크립트에서 바꾸는 행은 없다.

-- 검증: 임베딩이 있는 옷 수와 차원 (차원은 1536만 나와야 함)
-- SELECT vector_dims(embedding) AS dims, count(*) FROM clothes WHERE embedding IS NOT NULL GROUP BY 1;

-- 롤백: 옛 코드는 새 컬럼을 모르므로 그대로 둬도 된다. 지우려면 (필요할 때만):
--       ALTER TABLE clothes DROP COLUMN embedding; -- 속성 컬럼도 같은 방식
