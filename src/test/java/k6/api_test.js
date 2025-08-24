import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    // ступенчатая нагрузка 1 -> 200 виртуальных пользователей
    stages: [
        { duration: '10s', target: 10 },
        { duration: '20s', target: 50 },
        { duration: '20s', target: 100 },
        { duration: '20s', target: 200 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        'http_req_failed': ['rate<0.01'],        // <1% ошибок
        'http_req_duration': ['p(95)<200'],      // 95-й перцентиль < 200ms
        'time_get': ['p(95)<150'],               // ужесточённо для /api/time
        'static_get': ['p(95)<250'],
        'echo_post': ['p(95)<250'],
    },
    summaryTrendStats: ['avg','min','med','p(90)','p(95)','p(99)','max','count'],
};

const timeTrend = new Trend('time_get');
const staticTrend = new Trend('static_get');
const echoTrend = new Trend('echo_post');
const okCounter = new Counter('ok_requests');

export default function () {
    // 1) GET /api/time
    {
        const res = http.get(`${BASE_URL}/api/time`, { tags: { endpoint: 'GET /api/time' }});
        check(res, {
            'time status 200': (r) => r.status === 200,
            'time is json': (r) => (r.headers['Content-Type'] || '').includes('application/json'),
        });
        timeTrend.add(res.timings.duration);
        if (res.status === 200) okCounter.add(1);
    }

    // 2) GET /
    {
        const res = http.get(`${BASE_URL}/`, { tags: { endpoint: 'GET /' }});
        check(res, {
            'root status 200 or 304': (r) => r.status === 200 || r.status === 304,
            'root is html': (r) => (r.headers['Content-Type'] || '').includes('text/html'),
        });
        staticTrend.add(res.timings.duration);
        if (res.status === 200 || res.status === 304) okCounter.add(1);
    }

    // 3) GET /static/index.html
    {
        const res = http.get(`${BASE_URL}/static/index.html`, { tags: { endpoint: 'GET /static/index.html' }});
        check(res, {
            'static status 200': (r) => r.status === 200,
            'static is html': (r) => (r.headers['Content-Type'] || '').includes('text/html'),
        });
        staticTrend.add(res.timings.duration);
        if (res.status === 200) okCounter.add(1);
    }

    // 4) POST /api/echo
    {
        const payload = 'Привет, k6!';
        const headers = { 'Content-Type': 'text/plain; charset=UTF-8' };
        const res = http.post(`${BASE_URL}/api/echo`, payload, { headers, tags: { endpoint: 'POST /api/echo' }});
        check(res, {
            'echo status 200': (r) => r.status === 200,
            'echo mirrors body': (r) => r.body === payload,
        });
        echoTrend.add(res.timings.duration);
        if (res.status === 200) okCounter.add(1);
    }

    sleep(0.2);
}
