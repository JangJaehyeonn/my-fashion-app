/**
 * API 응답 시간 측정 스크립트
 *
 * 실행 방법:
 *   k6 run -e JWT_TOKEN=<발급받은_JWT_토큰> k6-latency.js
 *
 * JWT 토큰 획득 방법:
 *   1. Android 앱 로그인 후 Logcat에서 accessToken 복사
 *   2. 또는 /api/auth/google, /api/auth/kakao 응답의 accessToken 사용
 *
 * GPT-4o 호출이 포함된 엔드포인트(코디/쇼핑 추천)는 비용 문제로 k6-ai.js에서 별도 측정.
 * 코디 진단(POST /api/diagnosis)은 이미지 멀티파트 업로드가 필요해 이 스크립트 대상에서 제외.
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";

const BASE_URL = "http://fashion-app-jh.duckdns.org";
const JWT_TOKEN = __ENV.JWT_TOKEN;

// 엔드포인트별 커스텀 메트릭
const trendGetMe = new Trend("latency_get_me", true);
const trendGetWeather = new Trend("latency_get_weather", true);
const trendGetDiagnosisHistory = new Trend("latency_get_diagnosis_history", true);

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
    latency_get_weather: ["p(95)<1000"],
    latency_get_diagnosis_history: ["p(95)<500"],
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

  // 2. GET /api/weather (Redis 캐싱 포함)
  res = http.get(`${BASE_URL}/api/weather`, { headers });
  trendGetWeather.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, { "[GET /weather] status 200": (r) => r.status === 200 });

  sleep(0.2);

  // 3. GET /api/diagnosis (내 진단 이력 목록)
  res = http.get(`${BASE_URL}/api/diagnosis`, { headers });
  trendGetDiagnosisHistory.add(res.timings.duration);
  errorRate.add(res.status !== 200);
  check(res, { "[GET /diagnosis] status 200": (r) => r.status === 200 });

  sleep(0.5);
}
