BEGIN;
ALTER TABLE KUBERNETES_UTILIZATION_DATA ADD COLUMN IF NOT EXISTS STORAGEREQUESTVALUE DOUBLE PRECISION;
ALTER TABLE KUBERNETES_UTILIZATION_DATA ADD COLUMN IF NOT EXISTS STORAGEUSAGEVALUE DOUBLE PRECISION;

ALTER TABLE UTILIZATION_DATA ADD COLUMN IF NOT EXISTS AVGSTORAGEREQUESTVALUE DOUBLE PRECISION;
ALTER TABLE UTILIZATION_DATA ADD COLUMN IF NOT EXISTS AVGSTORAGEUSAGEVALUE DOUBLE PRECISION;
ALTER TABLE UTILIZATION_DATA ADD COLUMN IF NOT EXISTS AVGSTORAGECAPACITYVALUE DOUBLE PRECISION;

ALTER TABLE BILLING_DATA ADD COLUMN IF NOT EXISTS STORAGEACTUALIDLECOST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA ADD COLUMN IF NOT EXISTS STORAGEUNALLOCATEDCOST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA ADD COLUMN IF NOT EXISTS STORAGEUTILIZATIONVALUE DOUBLE PRECISION;
ALTER TABLE BILLING_DATA ADD COLUMN IF NOT EXISTS STORAGEREQUEST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA ADD COLUMN IF NOT EXISTS STORAGEMBSECONDS DOUBLE PRECISION;
ALTER TABLE BILLING_DATA ADD COLUMN IF NOT EXISTS STORAGECOST DOUBLE PRECISION;

ALTER TABLE BILLING_DATA_HOURLY ADD COLUMN IF NOT EXISTS STORAGEACTUALIDLECOST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_HOURLY ADD COLUMN IF NOT EXISTS STORAGEUNALLOCATEDCOST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_HOURLY ADD COLUMN IF NOT EXISTS STORAGEUTILIZATIONVALUE DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_HOURLY ADD COLUMN IF NOT EXISTS STORAGEREQUEST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_HOURLY ADD COLUMN IF NOT EXISTS STORAGEMBSECONDS DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_HOURLY ADD COLUMN IF NOT EXISTS STORAGECOST DOUBLE PRECISION;

ALTER TABLE BILLING_DATA_AGGREGATED ADD COLUMN IF NOT EXISTS STORAGEACTUALIDLECOST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_AGGREGATED ADD COLUMN IF NOT EXISTS CPUACTUALIDLECOST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_AGGREGATED ADD COLUMN IF NOT EXISTS MEMORYACTUALIDLECOST DOUBLE PRECISION;

ALTER TABLE BILLING_DATA_AGGREGATED ADD COLUMN IF NOT EXISTS STORAGEUNALLOCATEDCOST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_AGGREGATED ADD COLUMN IF NOT EXISTS MEMORYUNALLOCATEDCOST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_AGGREGATED ADD COLUMN IF NOT EXISTS CPUUNALLOCATEDCOST DOUBLE PRECISION;

ALTER TABLE BILLING_DATA_AGGREGATED ADD COLUMN IF NOT EXISTS STORAGECOST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_AGGREGATED ADD COLUMN IF NOT EXISTS CPUBILLINGAMOUNT DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_AGGREGATED ADD COLUMN IF NOT EXISTS MEMORYBILLINGAMOUNT DOUBLE PRECISION;

ALTER TABLE BILLING_DATA_AGGREGATED ADD COLUMN IF NOT EXISTS STORAGEREQUEST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_AGGREGATED ADD COLUMN IF NOT EXISTS STORAGEUTILIZATIONVALUE DOUBLE PRECISION;
COMMIT;