-- Phase 0 (2026-07-26 피봇): 옷장/캘린더 도메인 제거에 따른 수동 DB 정리 스크립트
--
-- ddl-auto: update 는 사용하지 않는 테이블/컬럼을 자동으로 지워주지 않으므로,
-- Clothes/Outfit/OutfitCalendar 엔티티를 코드에서 삭제한 뒤 이 스크립트를 직접 실행해서
-- 로컬/EC2 운영 DB의 실제 테이블을 정리해야 한다.
--
-- 실행 전 확인 사항:
--   - 반드시 운영 DB 백업 후 실행할 것 (되돌릴 수 없는 DROP)
--   - 로컬: docker exec -it fashionapp-db psql -U <user> -d <db> -f phase0_cleanup.sql
--   - EC2:  운영 DB 컨테이너에 동일하게 적용

DROP TABLE IF EXISTS outfit_calendar;
DROP TABLE IF EXISTS outfit_items;
DROP TABLE IF EXISTS outfits;
DROP TABLE IF EXISTS clothes;
