import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';

export const options = {
  vus: 1,
  iterations: 1,
};

export default function () {
  console.log('Creating 100 test users...');

  for (let i = 1; i <= 100; i++) {
    const payload = JSON.stringify({
      email: `testuser${i}@example.com`,
      password: 'password123'
    });

    const res = http.post(`${BASE_URL}/users`, payload, {
      headers: { 'Content-Type': 'application/json' },
    });

    const success = check(res, {
      [`User ${i} created`]: (r) => r.status === 200 || r.status === 201 || r.status === 409, // 409 = already exists
    });

    if (!success) {
      console.error(`Failed to create user ${i}: ${res.status} - ${res.body}`);
    }

    if (i % 10 === 0) {
      console.log(`Created ${i}/100 users`);
    }
  }

  console.log('User setup completed!');
}
