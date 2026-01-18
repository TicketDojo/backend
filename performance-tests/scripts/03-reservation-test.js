import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const reservationDuration = new Trend('reservation_duration');
const optimisticLockConflicts = new Rate('optimistic_lock_conflicts');

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
  const startTime = new Date().getTime();

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

  const endTime = new Date().getTime();
  reservationDuration.add(endTime - startTime);

  const reservationSuccess = check(reservationRes, {
    'reservation created': (r) => r.status === 200 || r.status === 201,
  });

  if (!reservationSuccess) {
    errorRate.add(1);
    return;
  }

  const reservationId = reservationRes.json('id');

  // 3. Update reservation state (simulate concurrent updates)
  const updateRes = http.put(
    `${BASE_URL}/api/ticketing/reservation/${reservationId}/state`,
    JSON.stringify({ state: 'PAYING' }),
    {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
    }
  );

  const updateSuccess = check(updateRes, {
    'state updated': (r) => r.status === 200,
    'optimistic lock conflict': (r) => r.status === 409, // Conflict due to version mismatch
  });

  if (updateRes.status === 409) {
    optimisticLockConflicts.add(1);
  } else if (!updateSuccess) {
    errorRate.add(1);
  }

  sleep(0.5);
}

export function handleSummary(data) {
  return {
    'reservation-summary.json': JSON.stringify(data, null, 2),
  };
}
