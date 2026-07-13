/**
 * 부하 테스트 스크립트 (동시 사용자 처리 능력 측정)
 *
 * 실행 방법:
 *   k6 run -e JWT_TOKEN=<발급받은_JWT_토큰> k6-load.js
 *
 * 주의: AI 추천/분류 엔드포인트는 GPT-4o 비용 문제로 제외
 *       DB 읽기 중심 엔드포인트만 포함
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";

const BASE_URL = "http://fashion-app-jh.duckdns.org";
const JWT_TOKEN = __ENV.JWT_TOKEN;

const errorRate = new Rate("error_rate");
const trendAll = new Trend("latency_all", true);
const requestCount = new Counter("request_count");

const headers = {
  Authorization: `Bearer ${JWT_TOKEN}`,
  "Content-Type": "application/json",
};

export const options = {
  scenarios: {
    load_test: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 1 },   // 워밍업
        { duration: "1m", target: 20 },   // 점진적 증가
        { duration: "2m", target: 20 },   // 유지 (Sustained Load)
        { duration: "1m", target: 50 },   // 피크
        { duration: "30s", target: 0 },   // 쿨다운
      ],
    },
  },
  thresholds: {
    // p95 응답 시간 500ms 이하
    latency_all: ["p(95)<500", "p(99)<1000"],
    // 오류율 1% 미만
    error_rate: ["rate<0.01"],
    // HTTP 실패율 1% 미만
    http_req_failed: ["rate<0.01"],
  },
};

// 테스트 엔드포인트 목록 (DB 읽기 위주)
const ENDPOINTS = [
  { method: "GET", url: "/api/users/me", body: null },
  { method: "GET", url: "/api/clothes", body: null },
  { method: "GET", url: "/api/outfits", body: null },
  { method: "GET", url: "/api/calendar", body: null },
  { method: "GET", url: "/api/weather?nx=60&ny=127", body: null },
];

export default function () {
  // 각 VU가 엔드포인트를 순서대로 호출
  const endpoint = ENDPOINTS[Math.floor(Math.random() * ENDPOINTS.length)];
  const url = `${BASE_URL}${endpoint.url}`;

  let res;
  if (endpoint.method === "GET") {
    res = http.get(url, { headers });
  } else {
    res = http.post(url, endpoint.body, { headers });
  }

  trendAll.add(res.timings.duration);
  requestCount.add(1);

  const ok = res.status >= 200 && res.status < 300;
  errorRate.add(!ok);

  check(res, {
    "status is 2xx": (r) => r.status >= 200 && r.status < 300,
    "response time < 1s": (r) => r.timings.duration < 1000,
  });

  sleep(Math.random() * 0.5 + 0.1); // 0.1~0.6s 랜덤 딜레이 (실제 사용자 패턴 모사)
}
