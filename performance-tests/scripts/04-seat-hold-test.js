import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const seatHoldDuration = new Trend('seat_hold_duration');
const seatConflicts = new Counter('seat_conflicts');
const successfulHolds = new Counter('successful_holds');

// Test configuration
export const options = {
  stages: [
    { duration: '10s', target: 100 },
    { duration: '1m', target: 100 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    errors: ['rate<0.1'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const TOTAL_SEATS = 100; // Adjust based on your test data

export default function () {
  const userId = (__VU % 100) + 1;

  // 1. Login
  const loginPayload = JSON.stringify({
    email: `testuser${userId}@example.com`,
    password: 'password123'
  });

  const loginRes = http.post(`${BASE_URL}/api/auth/login`, loginPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  if (!check(loginRes, { 'login successful': (r) => r.status === 200 })) {
    errorRate.add(1);
    return;
  }

  const token = loginRes.json('accessToken');

  // 2. Create reservation
  const reservationRes = http.post(
    `${BASE_URL}/api/ticketing/reservation`,
    null,
    {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
    }
  );

  if (!check(reservationRes, { 'reservation created': (r) => r.status === 200 || r.status === 201 })) {
    errorRate.add(1);
    return;
  }

  const reservationId = reservationRes.json('id');

  // 3. Try to hold a seat (introduce contention by using fewer seats than VUs)
  const seatId = Math.floor(Math.random() * TOTAL_SEATS) + 1;

  const startTime = new Date().getTime();

  const holdPayload = JSON.stringify({
    seatId: seatId,
    reservationId: reservationId
  });

  const holdRes = http.post(
    `${BASE_URL}/api/ticketing/seat/hold`,
    holdPayload,
    {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
    }
  );

  const endTime = new Date().getTime();
  seatHoldDuration.add(endTime - startTime);

  const holdSuccess = check(holdRes, {
    'seat hold successful': (r) => r.status === 200,
    'seat already held': (r) => r.status === 409 || r.status === 400, // Conflict
    'response time < 500ms': (r) => r.timings.duration < 500,
  });

  if (holdRes.status === 200) {
    successfulHolds.add(1);
  } else if (holdRes.status === 409 || holdRes.status === 400) {
    seatConflicts.add(1);
  } else {
    errorRate.add(1);
  }

  sleep(0.5);
}

export function handleSummary(data) {
  return {
    'seat-hold-summary.json': JSON.stringify(data, null, 2),
  };
}
