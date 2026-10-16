# Byawasaya production implementation status

The codebase is moving beyond an MVP toward a commercial Nepal-focused multi-tenant commerce SaaS. Source implementation and automated checks are not production certification. The detailed live-service and legal release gates are in [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md).

## Implemented in source

- Platform, tenant, staff and customer accounts with protected sessions, role checks, CSRF, account recovery and audit events.
- Mandatory platform-admin TOTP MFA with encrypted seeds, recovery codes, replay protection and audited reset.
- Tenant-owned eSewa/Khalti merchant connections with encrypted credentials and server-side payment verification.
- Manual platform subscription invoicing with monthly/annual price snapshots, tenant invoice visibility, audited full-payment recording, and void/reissue controls. This does not collect payment automatically.
- Private identity-document storage with backend-side encryption and fail-closed ClamAV scanning before persistence.
- PostgreSQL migrations, tenant provisioning, store/catalog/order workflows, plan limits, trial/subscription administration, and production Compose scaffolding.

## Remaining release gates

- Repeatable PostgreSQL/Redis integration and cross-tenant isolation tests; full browser acceptance for MFA, upload scanning, checkout and admin workflows.
- Actual ClamAV container startup and clean/infected/unavailable upload verification in the deployment environment.
- Real SMTP/OAuth and each tenant's eSewa/Khalti sandbox/live account checks; refunds and disputes are not automated.
- Manual billing payment instructions, compliant tax-invoice policy and delivery; automated recurring SaaS billing and dunning; custom-domain ownership/TLS automation; region-based delivery rates. Product variants are implemented in source but still need database-backed upgrade and checkout verification.
- Backup restore and key-recovery drills, monitoring/alert routing, security and load testing, incident rehearsal, and Nepal legal review for privacy, consumer, e-commerce, tax and identity-document retention requirements.

The current backend suite passes 16 tests. Frontend lint/build and development/production Compose configuration checks pass. The invoice and payment flow also passed a disposable PostgreSQL API smoke test. See the detailed [acceptance checklist](docs/ACCEPTANCE_CHECKLIST.md) and [deployment runbook](docs/PRODUCTION_DEPLOYMENT.md) for the remaining deployment steps.
