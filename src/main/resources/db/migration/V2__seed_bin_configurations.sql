-- Seed data: two synthetic BIN configurations so the API is usable immediately after startup.
-- BIN codes below are synthetic. Administrators can edit rules (rule_version is incremented on every change).
INSERT INTO bin_configurations
    (bin_code, name, billing_cycle_day, payment_due_days, min_due_percentage, min_due_floor,
     finance_charge_monthly_rate, gst_rate, late_fee_percentage, late_fee_floor, late_fee_cap,
     late_fee_threshold, rule_version, created_at, updated_at, version)
VALUES
    ('411111', 'Classic (seed)', 15, 20, 5.0000, 200.00, 3.5000, 18.0000, 10.0000, 100.00, 1000.00, 500.00, 1,
     CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0),
    ('520000', 'Platinum (seed)', 1, 20, 5.0000, 250.00, 3.0000, 18.0000, 8.0000, 150.00, 1200.00, 500.00, 1,
     CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0);
