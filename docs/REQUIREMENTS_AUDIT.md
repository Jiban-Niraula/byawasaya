# Byawasaya requirements audit

**Audited:** 2026-10-16  
**Scope:** source implementation in this repository, the 39-item agreed checklist, and focused build/database checks. This is not a production launch certification.

## Status key

- **Implemented:** the feature is present in source. Production behavior can still need deployment configuration.
- **Partial:** some of the requirement exists, but a material limit or verification gate remains.
- **Not implemented:** there is no working source implementation; a plan or architecture note does not count as implementation.

## Agreed 39-item checklist

| # | Requirement | Status | Evidence and limits |
|---:|---|---|---|
| 1 | Nepal-first platform for local businesses | Partial | NPR pricing and Nepal address fields are present. Geographic validation, Nepali-language support, and local launch operations are not verified. |
| 2 | Subscription SaaS operated by Byawasaya | Implemented | Plans, trials, subscription state and monthly/annual manual invoices exist. Recurring collection is not implemented. |
| 3 | Retailers and wholesalers selling physical products | Implemented | Product catalog, retail/wholesale pricing and quantity threshold drive checkout. There is no service listing model. |
| 4 | Services and service listings excluded | Implemented | Catalog and order flows are product-based; no service listing workflow is exposed. |
| 5 | One platform serves independent businesses | Implemented | Shared platform database with generated tenant-prefixed commerce tables and tenant accounts. |
| 6 | Suitable for BCA project and future commercial launch | Partial | Architecture and workflows are substantial, but commercial acceptance gates below remain open. |
| 7 | Super-admin dashboard | Implemented | Platform administration, tenant/application management, plans, billing and MFA are present. |
| 8 | Tenant workspace for each business owner | Implemented | Store owners have catalog, order, settings, staff and billing areas. |
| 9 | Staff accounts with role-based permissions | Partial | Staff membership and manager checks exist. Role-boundary and permission-matrix integration checks remain. |
| 10 | Public tenant storefront | Implemented | Public storefront and checkout endpoints resolve tenant stores. |
| 11 | Customer accounts and relevant order history | Implemented | Email/password customer accounts, verified-account order linking and private cross-store history exist. |
| 12 | Tenant-specific store identity and catalog | Implemented | Store profile/settings and products are scoped to a tenant. |
| 13 | Storefronts on Byawasaya subdomains | Partial | One-level tenant-subdomain resolution is implemented; DNS, wildcard certificates and production routing must be configured and tested. `/store/{slug}` is also available. |
| 14 | Custom domains supported or planned | Partial | A safe registry/TXT-verification/TLS design is documented. Ownership verification and certificate automation are not implemented. |
| 15 | Backend tenant data and permission isolation | Partial | Trusted prefix lookup and backend scope checks exist; repeatable cross-tenant database tests and security review are still required. |
| 16 | Standard layouts; no drag-and-drop builder | Implemented | Tenants use a standard storefront. There is no drag-and-drop builder. |
| 17 | More selectable templates can be introduced later | Partial | A template key is stored, but only the current standard template is available for selection. |
| 18 | Products, categories, images and variants where needed | Partial | Product images and optional server-priced variants are implemented. Category is free text rather than managed category CRUD; finish variant checkout/API acceptance. |
| 19 | Retail and wholesale modes | Implemented | Checkout supports both sales types. |
| 20 | Wholesale prices and minimum quantities | Implemented | Threshold and price are stored per product and checked server-side. |
| 21 | Cart and checkout | Implemented | Storefront cart submits orders to the backend. |
| 22 | Order creation, history and management | Implemented | Orders are stored, listed and transitioned through a restricted lifecycle; customers can view linked history. |
| 23 | Customer information and delivery addresses | Implemented | Checkout collects contact and Nepal address fields. |
| 24 | Shipping and delivery configuration | Partial | A tenant-configured flat delivery fee is applied. Province/district rate tables and courier integrations are absent. |
| 25 | No mandatory detailed stock-count requirement | Implemented | Quantities can be ordered, but inventory stock is not tracked, as agreed. |
| 26 | Cash on delivery | Implemented | COD eligibility is checked by the backend and receipt can be recorded only after delivery. |
| 27 | Architecture for verified online payments | Partial | Tenant-owned eSewa/Khalti credentials and verification flows exist. Each provider's sandbox/live operation still needs to be verified. |
| 28 | Store/product rules can disable COD or require online payment | Implemented | Store and product payment policies are enforced during checkout. |
| 29 | Advance or advance-plus-COD orders | Implemented | Advance percentage and payment method are calculated and validated server-side. |
| 30 | Payment status separate from order status | Implemented | Independent payment and fulfilment states are persisted and transitioned. |
| 31 | Online payment not claimed live until gateway is verified | Partial | The code does provider-side checks; no evidence of successful merchant-specific live acceptance is stored in this repo. Keep production online checkout gated until verified. |
| 32 | Backend validates eligibility, amounts and order rules | Implemented | Checkout recalculates catalog prices, delivery fee, minimum amount, provider eligibility and advance due. |
| 33 | Platform owner can manage tenants | Implemented | Platform tenant status and onboarding review controls exist. |
| 34 | Plans, pricing and limits | Implemented | Plan pricing/trial/product/staff limits are stored; product and staff limits have backend enforcement. |
| 35 | Subscription status, billing and payment records | Partial | Audited manual invoice and payment records exist. Automated subscription collection, retries and dunning do not. |
| 36 | Suspension and reactivation controls | Implemented | Platform controls and server-side operational checks exist. Recheck lifecycle behavior in deployment acceptance. |
| 37 | Platform analytics and overview | Implemented | Platform dashboard reports tenant/application/order/sales overview data. Validate figures against production data before launch. |
| 38 | Tenant analytics and order reporting | Partial | Merchant workspace provides order lists and basic counts/totals; richer date-range and export reporting is absent. |
| 39 | Staff roles and audit logs | Partial | Staff permission checks and platform audit events exist, but audit coverage and access boundaries need a complete acceptance pass. |

## Agreed technical architecture

| Component | Source status | Verification boundary |
|---|---|---|
| Java 21 / Spring Boot backend | Implemented | Maven compiler targets Java 21. This run used JDK 25; run the release pipeline on Java 21. |
| React / TypeScript frontend | Implemented | ESLint and production TypeScript/Vite build pass. |
| Modular monolith | Implemented | Domain packages run in one Spring Boot service. |
| PostgreSQL | Implemented | Flyway is authoritative; Hibernate validates rather than mutating production schema. |
| Spring Security and backend roles | Implemented | Route and tenant-service checks exist; integration security testing remains. |
| Tenant isolation | Partial | Trusted registry prefixes and scoped queries exist. Prefix isolation still needs repeatable cross-tenant testing and review. |
| Redis | Implemented | Configured for cache/session acceleration; target-environment health and failover need verification. |
| Docker Compose | Implemented | Development and production topologies are present; production secrets and services need deployment configuration. |
| NGINX reverse proxy | Implemented | TLS proxy configuration exists; live certificate, headers and domain routing are deployment gates. |
| Jenkins | Partial | A Jenkins pipeline file builds/lints/tests/images. Jenkins controller, credentials, artifact retention and an actual release run are not verified. |
| Prometheus / Grafana | Partial | Scrape and dashboard wiring exists. Alert rules, routing and an operational owner are not verified. |
| Flyway | Implemented | Nine versioned migrations are present and applied successfully to a disposable PostgreSQL 17 database. |
| Private persistent object storage | Partial | Private identity documents use encrypted S3-compatible storage. Production endpoint, persistence, key recovery and object restore need deployment verification. Store images/logos currently use tenant PostgreSQL `BYTEA`. |

## Security and reliability checks

| Requirement | Status | Evidence / remaining work |
|---|---|---|
| Tenant cannot read or modify another tenant's data | Partial | Prefix resolver validates registered identifiers; run automated two-tenant tests for every resource and role. |
| Backend authorization independent of UI | Implemented | Spring Security, tenant filters and service checks enforce server access. Confirm using direct API requests in acceptance. |
| Distinct super-admin, owner and staff permissions | Partial | Separate account types and staff permissions exist; finish role-matrix tests. |
| Plan limits enforced server-side | Implemented | Product/staff services check assigned plan limits. |
| Secrets out of source control | Implemented in source setup | `.env` is ignored and example files are provided. Verify CI/deployment secret stores and rotate any credentials previously used outside this repo. |
| Password hashing and sensitive-data encryption | Implemented | Spring password encoder, encrypted provider/document/MFA secrets and hashed tokens/codes are present. Key backup/rotation still needs an approved procedure. |
| Authentication and important admin actions logged | Partial | Rate-limit counters and audit events exist; verify event completeness, retention and alerting. |
| Backend order totals and payment eligibility | Implemented | Server-side price, delivery, minimum, advance and provider validation. |
| Idempotent checkout retries | Implemented in source | Idempotency keys and payload hashes are present. Add concurrent PostgreSQL tests for both tenant-prefixed and legacy paths. |
| Production backup and recovery | Partial | Backup/checksum scripts and runbook exist; off-host restore drill and recovery metrics are outstanding. |
| Custom domains verified and served over HTTPS | Not implemented | Custom-domain registry, proof challenge and automated certificate lifecycle are absent. |
| Nepal legal, consumer, privacy and tax review | External gate | Obtain documented local legal/accounting review before production data or payments. |

## Verification performed for this audit

- `cd backend && ./mvnw -B test`: passed, 19 tests, no failures.
- `cd frontend && npm run lint && npm run build`: passed.
- Disposable PostgreSQL 17: Flyway migrations V1–V9 applied; backend started with Hibernate schema validation; an existing tenant at schema version 2 upgraded at startup to version 3; the prefixed `product_variants` table and `order_items.variant_id` / `variant_label` columns were confirmed. The disposable database and app process were removed/stopped afterward.
- This does not exercise an authenticated end-to-end variant purchase or replace the cross-tenant, payment-provider, upload, backup-restore, browser, security, load or legal release checks below.

## Production release gates still open

1. Repeatable PostgreSQL tests for new/existing tenant provisioning, variant add/edit/disable and checkout snapshots, same-key concurrent checkout, and full two-tenant access isolation.
2. Run build and acceptance using the required Java 21 runtime, and execute an authenticated desktop/mobile browser pass for all key roles and workflows.
3. Verify actual tenant-owned eSewa and Khalti sandbox/live accounts, callbacks, delayed/duplicate/forged events, cancellation, reconciliation and refund/dispute operations before enabling live checkout.
4. Configure production DNS, wildcard TLS, SMTP, OAuth, private S3-compatible storage, secrets, Jenkins credentials, monitoring alerts and operational ownership. Custom domains remain unavailable until ownership proof and TLS automation are implemented.
5. Complete off-host backup restoration and encryption-key recovery/rotation drills; record recovery time and data-loss objectives.
6. Finish security, penetration and load/abuse testing; test clean, EICAR, unavailable-scanner and oversized uploads against the deployment ClamAV service.
7. Obtain Nepal-specific legal/accounting approval for e-commerce, privacy, consumer protection, identity-document retention, payment-provider terms and tax invoices.
8. Define and publish manual subscription invoice instructions, verified-receipt reconciliation and delivery policy. Automated recurring SaaS billing remains absent.
