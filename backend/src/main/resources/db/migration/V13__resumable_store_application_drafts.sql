ALTER TABLE tenant_applications
    ALTER COLUMN owner_account_id DROP NOT NULL;

ALTER TABLE tenant_applications
    ADD COLUMN draft_token_hash VARCHAR(64);

CREATE UNIQUE INDEX uk_tenant_applications_draft_token_hash
    ON tenant_applications (draft_token_hash)
    WHERE draft_token_hash IS NOT NULL;
