BEGIN;
ALTER TABLE BILLING_DATA ADD COLUMN IF NOT EXISTS NETWORKCOST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_HOURLY ADD COLUMN IF NOT EXISTS NETWORKCOST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA ADD COLUMN IF NOT EXISTS PRICINGSOURCE TEXT;
ALTER TABLE BILLING_DATA_HOURLY ADD COLUMN IF NOT EXISTS PRICINGSOURCE TEXT;
COMMIT;