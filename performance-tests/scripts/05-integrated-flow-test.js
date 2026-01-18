import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const fullFlowDuration = new Trend('full_flow_duration');
const queueEntryDuration = new Trend('queue_entry_duration');
const seatHoldDuration = new Trend('seat_hold_duration');

// Test configuration - Simulates ticket opening scenario
export const options = {
  stages: [
    { duration: '5s', target: 100 },    // Rapid ramp-up (ticket opens)
    { duration: '1m', target: 100 },    // Sustained load
    { duration: '10s', target: 50 },    // Some users give up
    { duration: '10s', target: 0 },     // Ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],  // 95% requests under 2s for full flow
    errors: ['rate<0.1'],
    full_flow_duration: ['p(95)<5000'], // Full flow under 5s for 95% users
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const TOTAL_SEATS = 100;

export default function () {
  const flowStartTime = new Date().getTime();
  const userId = (__VU % 100) + 1;

  // Step 1: Login
  const loginPayload = JSON.stringify({
    email: `testuser${userId}@example.com`,
    password: 'password123'
  });

  const loginRes = http.post(`${BASE_URL}/api/auth/login`, loginPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  if (!check(loginRes, { 'login successful': (r) => r.status === 200 })) {
    console.error(`Login failed for user ${userId}`);
    errorRate.add(1);
    return;
  }

  const token = loginRes.json('accessToken');

  // Step 2: Enter queue
  const queueStartTime = new Date().getTime();

  const queueRes = http.post(
    `${BASE_URL}/api/queue/enter`,
    null,
    {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
    }
  );

  queueEntryDuration.add(new Date().getTime() - queueStartTime);

  if (!check(queueRes, { 'queue entry successful': (r) => r.status === 200 || r.status === 201 })) {
    console.error(`Queue entry failed for user ${userId}`);
    errorRate.add(1);
    return;
  }

  const queueToken = queueRes.json('token');
  const queueStatus = queueRes.json('status');

  // Step 3: Wait if in WAITING status (poll until ACTIVE)
  let pollCount = 0;
  const maxPolls = 10;

  if (queueStatus === 'WAITING') {
    while (pollCount < maxPolls) {
      sleep(2); // Poll every 2 seconds

      const statusRes = http.get(
        `${BASE_URL}/api/queue/status`,
        {
          headers: {
            'Authorization': `Bearer ${token}`,
            'Queue-Token': queueToken
          },
        }
      );

      if (statusRes.status === 200 && statusRes.json('status') === 'ACTIVE') {
        break;
      }

      pollCount++;
    }

    if (pollCount >= maxPolls) {
      console.log(`User ${userId} gave up waiting in queue`);
      return; // Not an error, just couldn't get in
    }
  }

  // Step 4: Create reservation
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
    console.error(`Reservation creation failed for user ${userId}`);
    errorRate.add(1);
    return;
  }

  const reservationId = reservationRes.json('id');

  // Step 5: Hold a seat
  const seatId = Math.floor(Math.random() * TOTAL_SEATS) + 1;
  const seatStartTime = new Date().getTime();

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

  seatHoldDuration.add(new Date().getTime() - seatStartTime);

  const holdSuccess = check(holdRes, {
    'seat hold successful or conflict': (r) => r.status === 200 || r.status === 409 || r.status === 400,
  });

  if (!holdSuccess) {
    console.error(`Seat hold failed for user ${userId}: ${holdRes.status}`);
    errorRate.add(1);
  }

  // Step 6: If successful, update reservation to PAYING
  if (holdRes.status === 200) {
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

    check(updateRes, {
      'state updated to PAYING': (r) => r.status === 200,
    });
  }

  // Record full flow duration
  const flowEndTime = new Date().getTime();
  fullFlowDuration.add(flowEndTime - flowStartTime);

  sleep(1);
}

export function handleSummary(data) {
  return {
    'integrated-flow-summary.json': JSON.stringify(data, null, 2),
  };
}
