-- Proof-of-delivery photos move to object storage (MinIO). New photos keep only their object key here;
-- photos uploaded before this migration keep their bytes in "data" and are still served from it.
ALTER TABLE shipments.delivery_evidence
    ADD COLUMN object_key VARCHAR(200) NULL,
    ALTER COLUMN data DROP NOT NULL;

ALTER TABLE shipments.delivery_evidence
    ADD CONSTRAINT ck_delivery_evidence_content CHECK (data IS NOT NULL OR object_key IS NOT NULL);
