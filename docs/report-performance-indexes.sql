-- Report performance indexes (PostgreSQL)
-- 1) My report list pagination
CREATE INDEX IF NOT EXISTS idx_report_reporter_ctime
ON report (reporter_id, create_time DESC);

-- 2) Duplicate pending report detection
CREATE INDEX IF NOT EXISTS idx_report_dup_pending
ON report (reporter_id, target_type, target_id)
WHERE status = 'PENDING';

-- 3) Admin list by status + time
CREATE INDEX IF NOT EXISTS idx_report_status_ctime
ON report (status, create_time DESC);
