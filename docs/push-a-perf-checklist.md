# /push/a Perf Checklist

## 1) Single endpoint load test (separate from health checks)
- Only test: `GET /push/a`
- Concurrency: 100
- Total requests: 1000
- Do not include `/actuator/health` in the same test plan.

## 2) Enable SQL + timing logs (pressure environment only)
- Keep Hibernate SQL and bind logs enabled for test env.
- Observe application logs for:
  - `[push.a] perf ...`
  - `[push.a] loadByIds ...`
  - cache refill logs (`fill cache`, `fill type cache`)

## 3) EXPLAIN ANALYZE SQL templates

### 3.1 Weekly/default candidate query
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.postings_id
FROM postings p
WHERE p.create_time >= NOW() - INTERVAL '7 day'
  AND p.deleted = false
  AND p.status = 1
  AND p.audit_status = 1
ORDER BY p.create_time DESC
LIMIT 300;
```

### 3.2 Type candidate query
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.postings_id
FROM postings p
WHERE p.create_time >= NOW() - INTERVAL '7 day'
  AND p.deleted = false
  AND p.status = 1
  AND p.audit_status = 1
  AND p.type = :type
ORDER BY p.create_time DESC
LIMIT 200;
```

### 3.3 ID back-to-table query
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM postings
WHERE postings_id IN (:id_list);
```

## 4) Suggested indexes for /push/a
```sql
CREATE INDEX IF NOT EXISTS idx_postings_push_base
ON postings (create_time DESC)
WHERE deleted = false AND status = 1 AND audit_status = 1;

CREATE INDEX IF NOT EXISTS idx_postings_push_type
ON postings (type, create_time DESC)
WHERE deleted = false AND status = 1 AND audit_status = 1;
```

## 5) Slowpoint attribution table
- Filter/Sort slow: candidate query scans too many rows.
- Back-table slow: `IN (...)` fetch costs high.
- Cache miss/rebuild slow: frequent refill logs and long refill times.
- Degrade observed: fallback to random push (`timeout/fail ... fallback=random`).
