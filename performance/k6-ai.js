/**
 * AI 엔드포인트 응답 시간 측정 (GPT-4o 비용 고려 — 5회만 실행)
 *
 * 실행 방법:
 *   k6 run -e JWT_TOKEN=<발급받은_JWT_토큰> k6-ai.js
 *   k6 run -e JWT_TOKEN=<발급받은_JWT_토큰> -e BASE_URL=http://fashion-app-jh.duckdns.org k6-ai.js
 *   (BASE_URL 미지정 시 기본값 http://localhost:8080)
 *
 * 코디 진단(POST /api/diagnosis)은 이미지 멀티파트 업로드가 필요해 이 스크립트 대상에서 제외.
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const JWT_TOKEN = __ENV.JWT_TOKEN;

const trendWeather = new Trend("latency_ai_weather", true);
const trendSituationRecommend = new Trend("latency_ai_situation_recommend", true);
const trendShoppingRecommend = new Trend("latency_ai_shopping_recommend", true);
const errorRate = new Rate("error_rate");

const headers = {
  Authorization: `Bearer ${JWT_TOKEN}`,
  "Content-Type": "application/json",
};

export const options = {
  vus: 1,
  iterations: 5, // GPT-4o 비용 절약
  thresholds: {
    latency_ai_weather: ["p(95)<3000"],
    // 추천 API는 GPT-4o 포함이므로 10초 이내 허용
    latency_ai_situation_recommend: ["p(95)<10000"],
    latency_ai_shopping_recommend: ["p(95)<10000"],
    error_rate: ["rate<0.1"],
  },
};

export default function () {
  // 1. 날씨 조회 (기상청 API + Redis 캐싱)
  let res = http.get(`${BASE_URL}/api/weather`, { headers });
  trendWeather.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, {
    "[GET /weather] status 200": (r) => r.status === 200,
    "[GET /weather] has temperature": (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data?.temperature !== undefined;
      } catch {
        return false;
      }
    },
  });

  sleep(1);

  // 2. 오늘의 코디 추천 (GPT-4o — 날씨 + 상황 + 체형 프로필 기반)
  const situationBody = JSON.stringify({
    temperature: 20,
    condition: "맑음",
    situation: "출근",
  });
  res = http.post(`${BASE_URL}/api/outfits/recommend/situation`, situationBody, { headers });
  trendSituationRecommend.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, {
    "[POST /outfits/recommend/situation] status 200": (r) => r.status === 200,
  });

  sleep(2);

  // 3. 쇼핑 도우미 추천 (GPT-4o — 예산 + 상황 + 체형 프로필 기반)
  const shoppingBody = JSON.stringify({
    budget: 150000,
    situation: "출근",
  });
  res = http.post(`${BASE_URL}/api/shopping/recommend`, shoppingBody, { headers });
  trendShoppingRecommend.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, {
    "[POST /shopping/recommend] status 200": (r) => r.status === 200,
  });

  sleep(2);
}
