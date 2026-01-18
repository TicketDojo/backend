import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const queueEntryDuration = new Trend('queue_entry_duration');

// Test configuration
export const options = {
  stages: [
    { duration: '10s', target: 100 },  // Ramp-up to 100 VUs
    { duration: '1m', target: 100 },   // Stay at 100 VUs for 1 minute
    { duration: '10s', target: 0 },    // Ramp-down to 0
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],  // 95% requests under 1000ms
    errors: ['rate<0.05'],               // Error rate under 5%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';

export default function () {
  const userId = (__VU % 100) + 1;  // Use VU number to map to user IDs 1-100

  // 1. Login to get JWT token
  const loginPayload = JSON.stringify({
    email: `testuser${userId}@example.com`,
    password: 'password123'
  });

  const loginRes = http.post(`${BASE_URL}/login`, loginPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  const loginSuccess = check(loginRes, {
    'login successful': (r) => r.status === 200,
  });

  if (!loginSuccess) {
    console.error(`Login failed for user ${userId}: ${loginRes.status}`);
    errorRate.add(1);
    return;
  }

  const token = loginRes.headers['Access'];

  // 2. Enter queue
  const startTime = new Date().getTime();

  const queueRes = http.post(
    `${BASE_URL}/queue/jwt/enter`,
    null,
    {
      headers: {
        'access': token,
        'Content-Type': 'application/json'
      },
    }
  );

  const endTime = new Date().getTime();
  queueEntryDuration.add(endTime - startTime);

  const queueSuccess = check(queueRes, {
    'queue entry successful': (r) => r.status === 200 || r.status === 201,
    'response time < 1000ms': (r) => r.timings.duration < 1000,
  });

  errorRate.add(!queueSuccess);

  if (queueSuccess) {
    const queueToken = queueRes.json('token');

    // 3. Check queue status
    const statusRes = http.get(
      `${BASE_URL}/queue/status?token=${queueToken}`,
      {
        headers: {
          'access': token
        },
      }
    );

    check(statusRes, {
      'queue status retrieved': (r) => r.status === 200,
    });
  }

  sleep(1);
}

export function handleSummary(data) {
  return {
    'queue-entry-summary.json': JSON.stringify(data, null, 2),
  };
}
