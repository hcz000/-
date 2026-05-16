# Load Test Parameter Template (HTTP clients)

## Common HTTP client settings
- Keep-Alive: ON
- Connection reuse: ON
- Timeout(connect/read): 3000ms / 5000ms
- Retry on failure: OFF (for baseline truth)
- DNS cache: ON (default)

## Concurrency gradient (recommended)
1) Warmup
- Concurrency: 20
- Duration: 60s

2) Stage-1
- Concurrency: 50
- Duration: 120s

3) Stage-2
- Concurrency: 100
- Duration: 180s

4) Stage-3 (optional)
- Concurrency: 150
- Duration: 120s
- Only continue if success rate in Stage-2 >= 99%

## Request budget suggestion
- Per endpoint target total requests: 1000-3000
- Mixed business suite: control request ratio by real traffic assumptions

## JMeter quick baseline
- Thread Group: same as stages above
- HTTP Request Defaults:
  - Implementation: HttpClient4
  - Use KeepAlive: true
  - Connect Timeout: 3000
  - Response Timeout: 5000
- CSV/JSON assertions: ensure non-200 and timeout are counted explicitly

## k6 quick baseline
```js
export const options = {
  scenarios: {
    stage1: { executor: 'constant-vus', vus: 50, duration: '2m' },
    stage2: { executor: 'constant-vus', vus: 100, duration: '3m', startTime: '2m' }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500']
  }
};
```

## What to record each run
- QPS / p95 / p99
- Success rate
- Error type distribution (HTTP code / timeout / reset)
- Hikari metrics snapshot
