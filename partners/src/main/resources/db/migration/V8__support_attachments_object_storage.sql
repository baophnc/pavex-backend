-- Support request attachments move to object storage (MinIO). New files keep only their object key here;
-- files sent before this migration keep their bytes in "data" and are still served from it.
ALTER TABLE partners.support_request_attachments
    ADD COLUMN object_key VARCHAR(200) NULL,
    ALTER COLUMN data DROP NOT NULL;

ALTER TABLE partners.support_request_attachments
    ADD CONSTRAINT ck_support_attachment_content CHECK (data IS NOT NULL OR object_key IS NOT NULL);
