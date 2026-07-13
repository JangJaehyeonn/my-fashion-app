/**
 * AI 엔드포인트 응답 시간 측정 (GPT-4o 비용 고려 — 5회만 실행)
 *
 * 실행 방법:
 *   k6 run -e JWT_TOKEN=<발급받은_JWT_토큰> k6-ai.js
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";

const BASE_URL = "http://fashion-app-jh.duckdns.org";
const JWT_TOKEN = __ENV.JWT_TOKEN;

const trendRecommend = new Trend("latency_ai_recommend", true);
const trendWeather = new Trend("latency_ai_weather", true);
const errorRate = new Rate("error_rate");

const headers = {
  Authorization: `Bearer ${JWT_TOKEN}`,
  "Content-Type": "application/json",
};

export const options = {
  vus: 1,
  iterations: 5, // GPT-4o 비용 절약
  thresholds: {
    // 추천 API는 GPT-4o 포함이므로 10초 이내 허용
    latency_ai_recommend: ["p(95)<10000"],
    latency_ai_weather: ["p(95)<3000"],
    error_rate: ["rate<0.1"],
  },
};

export default function () {
  // 1. 날씨 조회 (기상청 API + Redis 캐싱)
  let res = http.get(`${BASE_URL}/api/weather?nx=60&ny=127`, { headers });
  trendWeather.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, {
    "[GET /weather] status 200": (r) => r.status === 200,
    "[GET /weather] has temperature": (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.temperature !== undefined;
      } catch {
        return false;
      }
    },
  });

  sleep(1);

  // 2. 코디 추천 (GPT-4o — 옷장 + 날씨 기반)
  res = http.post(`${BASE_URL}/api/outfits/recommend`, "{}", { headers });
  trendRecommend.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, {
    "[POST /outfits/recommend] status 200": (r) => r.status === 200,
  });

  sleep(2);
}
