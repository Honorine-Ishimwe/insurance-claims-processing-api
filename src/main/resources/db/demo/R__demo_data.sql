-- DEMO DATA: loaded ONLY when the "dev" profile is active (see application-dev.yml).
-- Never shipped to production: the prod profile reads only classpath:db/migration.
-- All demo passwords are: password
-- Repeatable migration with "insert if missing" so it is safe to re-run.

INSERT INTO app_user (username, password_hash, role, full_name)
SELECT 'member1', '$2a$10$pZ1fEk8lQZa12kANZVi.v.4gMg9taues8hYl3sI9ZsKkPmLRWY7xe', 'MEMBER', 'Maria Lopez'
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'member1');

INSERT INTO app_user (username, password_hash, role, full_name)
SELECT 'member2', '$2a$10$pZ1fEk8lQZa12kANZVi.v.4gMg9taues8hYl3sI9ZsKkPmLRWY7xe', 'MEMBER', 'James Chen'
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'member2');

INSERT INTO app_user (username, password_hash, role, full_name)
SELECT 'adjuster1', '$2a$10$pZ1fEk8lQZa12kANZVi.v.4gMg9taues8hYl3sI9ZsKkPmLRWY7xe', 'ADJUSTER', 'Priya Patel'
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'adjuster1');

INSERT INTO app_user (username, password_hash, role, full_name)
SELECT 'admin1', '$2a$10$pZ1fEk8lQZa12kANZVi.v.4gMg9taues8hYl3sI9ZsKkPmLRWY7xe', 'ADMIN', 'Alex Admin'
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'admin1');

-- POL-1001: fresh plan year, $500 deductible, plan pays 80%, $10,000 annual max
-- POL-1002: deductible already met, only $100 left of the annual maximum
-- POL-1003: lapsed policy from last year (claims fail ELIGIBILITY)
INSERT INTO policy (policy_number, member_id, start_date, end_date, status,
                    annual_deductible, deductible_met, coinsurance_rate, annual_maximum, benefits_paid_ytd)
SELECT 'POL-1001', (SELECT id FROM app_user WHERE username = 'member1'), DATE '2026-01-01', DATE '2026-12-31', 'ACTIVE',
       500.00, 0.00, 0.8000, 10000.00, 0.00
WHERE NOT EXISTS (SELECT 1 FROM policy WHERE policy_number = 'POL-1001');

INSERT INTO policy (policy_number, member_id, start_date, end_date, status,
                    annual_deductible, deductible_met, coinsurance_rate, annual_maximum, benefits_paid_ytd)
SELECT 'POL-1002', (SELECT id FROM app_user WHERE username = 'member2'), DATE '2026-01-01', DATE '2026-12-31', 'ACTIVE',
       500.00, 500.00, 0.8000, 5000.00, 4900.00
WHERE NOT EXISTS (SELECT 1 FROM policy WHERE policy_number = 'POL-1002');

INSERT INTO policy (policy_number, member_id, start_date, end_date, status,
                    annual_deductible, deductible_met, coinsurance_rate, annual_maximum, benefits_paid_ytd)
SELECT 'POL-1003', (SELECT id FROM app_user WHERE username = 'member1'), DATE '2025-01-01', DATE '2025-12-31', 'LAPSED',
       500.00, 500.00, 0.8000, 10000.00, 1200.00
WHERE NOT EXISTS (SELECT 1 FROM policy WHERE policy_number = 'POL-1003');
