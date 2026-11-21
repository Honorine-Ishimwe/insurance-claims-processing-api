-- Reference data: the catalog of service (CPT) codes and whether the plan covers them.
-- Needed in every environment. Admins can add or change entries via /api/v1/admin/covered-services.
INSERT INTO covered_service (service_code, description, covered) VALUES
    ('99213', 'Office visit, established patient, low complexity',      TRUE),
    ('99214', 'Office visit, established patient, moderate complexity', TRUE),
    ('80053', 'Comprehensive metabolic panel',                         TRUE),
    ('97110', 'Physical therapy, therapeutic exercise',                TRUE),
    ('15780', 'Dermabrasion (cosmetic)',                               FALSE);
