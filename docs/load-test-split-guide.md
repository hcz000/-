# Load Test Split Guide

## Goal
Separate health probes from business pressure tests to avoid cross-interference and false bottleneck attribution.

## Group A: Business endpoints only
Include examples:
- /push/a
- /user/search?email=...
- /report/my?page=1&size=20
- /primary-comment0/list?postingsId=...&page=1&size=20

Exclude:
- /actuator/health
- /actuator/health/readiness
- /actuator/health/liveness

## Group B: Probe endpoints only
- /actuator/health/liveness (light)
- /actuator/health/readiness (dependency-aware)

Do not run Group B mixed with Group A in the same plan/thread group.

## Success Criteria
- Group A success rate >= 99%
- Group B liveness stable, readiness only drops under real dependency stress

## During test observe
- Hikari active/idle/waiting connections
- Error split: HTTP 5xx vs client OSError/reset
- P95/P99 latency per endpoint
