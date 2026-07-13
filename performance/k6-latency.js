/**
 * API 응답 시간 측정 스크립트
 *
 * 실행 방법:
 *   k6 run -e JWT_TOKEN=<발급받은_JWT_토큰> k6-latency.js
 *
 * JWT 토큰 획득 방법:
 *   1. Android 앱 로그인 후 Logcat에서 accessToken 복사
 *   2. 또는 /api/auth/google, /api/auth/kakao 응답의 accessToken 사용
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";

const BASE_URL = "http://fashion-app-jh.duckdns.org";
const JWT_TOKEN = __ENV.JWT_TOKEN;

// 엔드포인트별 커스텀 메트릭
const trendGetMe = new Trend("latency_get_me", true);
const trendGetClothes = new Trend("latency_get_clothes", true);
const trendGetOutfits = new Trend("latency_get_outfits", true);
const trendGetCalendar = new Trend("latency_get_calendar", true);
const trendGetWeather = new Trend("latency_get_weather", true);
const trendPostRecommend = new Trend("latency_post_recommend", true);

const errorRate = new Rate("error_rate");

const headers = {
  Authorization: `Bearer ${JWT_TOKEN}`,
  "Content-Type": "application/json",
};

export const options = {
  // 1 VU, 각 엔드포인트를 20회 반복
  vus: 1,
  iterations: 20,
  thresholds: {
    latency_get_me: ["p(95)<300"],
    latency_get_clothes: ["p(95)<500"],
    latency_get_outfits: ["p(95)<500"],
    latency_get_calendar: ["p(95)<500"],
    latency_get_weather: ["p(95)<1000"],
    error_rate: ["rate<0.05"],
  },
};

export default function () {
  // 1. GET /api/users/me
  let res = http.get(`${BASE_URL}/api/users/me`, { headers });
  trendGetMe.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, { "[GET /users/me] status 200": (r) => r.status === 200 });

  sleep(0.2);

  // 2. GET /api/clothes
  res = http.get(`${BASE_URL}/api/clothes`, { headers });
  trendGetClothes.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, { "[GET /clothes] status 200": (r) => r.status === 200 });

  sleep(0.2);

  // 3. GET /api/outfits
  res = http.get(`${BASE_URL}/api/outfits`, { headers });
  trendGetOutfits.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, { "[GET /outfits] status 200": (r) => r.status === 200 });

  sleep(0.2);

  // 4. GET /api/calendar
  const now = new Date();
  res = http.get(
    `${BASE_URL}/api/calendar?year=${now.getFullYear()}&month=${now.getMonth() + 1}`,
    { headers }
  );
  trendGetCalendar.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, { "[GET /calendar] status 200": (r) => r.status === 200 });

  sleep(0.2);

  // 5. GET /api/weather (Redis 캐싱 포함)
  res = http.get(`${BASE_URL}/api/weather?nx=60&ny=127`, { headers });
  trendGetWeather.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, { "[GET /weather] status 200": (r) => r.status === 200 });

  sleep(0.5);
}

// 반복 1회당 GPT-4o 추천 API는 별도 시나리오로 측정 (비용 절약)
export function recommendTest() {
  const body = JSON.stringify({});
  const res = http.post(`${BASE_URL}/api/outfits/recommend`, body, { headers });
  trendPostRecommend.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, { "[POST /outfits/recommend] status 200": (r) => r.status === 200 });
}
